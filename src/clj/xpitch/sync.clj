(ns xpitch.sync
  "Background sync into an in-memory snapshot, persisted to disk so restarts are instant.

  Providers:
  - :football-data (default, no key): one CSV per league from football-data.co.uk.
  - :api-football (needs a key and a plan covering the season): standings, fixtures and
    per-match statistics. Finished-match statistics never change, so they're fetched once."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [xpitch.api-football :as api]
            [xpitch.data :as data]
            [xpitch.football-data :as fd]
            [xpitch.live :as live])
  (:import (java.time Instant LocalDate)))

(defn- env [k] (not-empty (System/getenv k)))

(defn- local-config []
  (let [f (io/file "config.local.edn")]
    (when (.exists f) (edn/read-string (slurp f)))))

(defn current-season
  "Seasons are named by their starting year; European seasons start in July/August."
  []
  (let [today (LocalDate/now)]
    (if (>= (.getMonthValue today) 7) (.getYear today) (dec (.getYear today)))))

(defn config
  "Env vars take precedence over config.local.edn."
  []
  (let [local (local-config)]
    {:provider (or (some-> (env "XPITCH_PROVIDER") keyword) (:provider local) :football-data)
     :api-key (or (env "API_FOOTBALL_KEY") (:api-football-key local))
     :season (or (some-> (env "XPITCH_SEASON") parse-long) (:season local) (current-season))
     :refresh-minutes (or (some-> (env "XPITCH_REFRESH_MINUTES") parse-long) (:refresh-minutes local) 60)
     :reserve (or (some-> (env "API_FOOTBALL_RESERVE") parse-long) (:reserve local) 5)
     :max-stat-batches (or (:max-stat-batches local) 10)}))

(defonce state
  ;; {:config {...} :leagues {league-id data} :errors {league-id msg} :last-sync Instant
  ;;  :failed-passes n :running? bool}
  (atom {:leagues {} :errors {}}))

(defn provider [] (get-in @state [:config :provider]))

(defn enabled?
  "True when a live provider is configured and usable."
  []
  (case (provider)
    :football-data true
    :api-football (boolean (get-in @state [:config :api-key]))
    false))

;; ---------- Disk cache ----------

(defn- cache-file [season league-id ext]
  (io/file ".cache" (name (provider)) (str season) (str (name league-id) ext)))

(defn- save! [f text]
  (io/make-parents f)
  (spit f text))

;; ---------- football-data.co.uk ----------

(defn- fd-load-cached [season league-id]
  (let [f (cache-file season league-id ".csv")]
    (when (.exists f)
      (assoc (fd/load-league league-id season (slurp f)) :fetched-at (.lastModified f)))))

(defn- fd-sync-league! [{:keys [season]} league-id]
  (let [text (fd/fetch-csv! (fd/csv-url league-id season))
        league-data (fd/load-league league-id season text)]
    (when (empty? (:standings league-data))
      (throw (ex-info (str "No " season "/" (inc season) " matches in the football-data.co.uk file yet")
                      {:type :empty})))
    (save! (cache-file season league-id ".csv") text)
    (swap! state assoc-in [:leagues league-id] (assoc league-data :fetched-at (System/currentTimeMillis)))))

;; ---------- API-Football ----------

(defn- stale? [fetched-at minutes]
  (or (nil? fetched-at)
      (> (- (System/currentTimeMillis) fetched-at) (* minutes 60 1000))))

(defn- af-load-cached [season league-id]
  (let [f (cache-file season league-id ".edn")]
    (when (.exists f)
      (try (edn/read-string (slurp f))
           (catch Exception e
             (println "Ignoring unreadable cache" (str f) "-" (ex-message e)))))))

(defn- af-sync-league!
  "Refreshes one league, saving after each step so partial progress survives errors."
  [{:keys [season refresh-minutes max-stat-batches] :as cfg} league-id]
  (let [api-id (get-in data/leagues [league-id :api-id])
        now (System/currentTimeMillis)
        update! (fn [f]
                  (let [d (f (get-in @state [:leagues league-id] {}))]
                    (swap! state assoc-in [:leagues league-id] d)
                    (save! (cache-file season league-id ".edn") (pr-str d))))
        current #(get-in @state [:leagues league-id])]
    (when (stale? (get-in (current) [:fetched :standings]) refresh-minutes)
      (let [rows (live/parse-standings (api/standings cfg api-id season))]
        (update! #(-> % (assoc :standings rows) (assoc-in [:fetched :standings] now)))))
    (when-not (get-in (current) [:fetched :codes])
      (let [codes (live/parse-team-codes (api/teams cfg api-id season))]
        (update! #(-> % (assoc :codes codes) (assoc-in [:fetched :codes] now)))))
    (when (stale? (get-in (current) [:fetched :fixtures]) refresh-minutes)
      (let [fixtures (live/parse-fixtures (api/finished-fixtures cfg api-id season))]
        (update! #(-> % (assoc :fixtures fixtures) (assoc-in [:fetched :fixtures] now)))))
    ;; Newest matches first, so a limited budget covers the current form.
    (let [missing (->> (:fixtures (current))
                       (remove #(contains? (:fixture-stats (current)) (:id %)))
                       (sort-by :date #(compare %2 %1))
                       (map :id))]
      (doseq [batch (take max-stat-batches (partition-all 20 missing))]
        (let [stats (live/parse-fixture-stats (api/fixtures-by-ids cfg batch))]
          (update! #(update % :fixture-stats merge stats)))))))

;; ---------- Loop ----------

(defn sync-all!
  "One pass over every league. Errors are recorded per league; a spent API budget stops the pass."
  []
  (let [cfg (:config @state)
        sync-league! (case (:provider cfg) :api-football af-sync-league! fd-sync-league!)]
    (try
      (doseq [league-id data/league-order]
        (try
          (sync-league! cfg league-id)
          (swap! state update :errors dissoc league-id)
          (catch clojure.lang.ExceptionInfo e
            (swap! state assoc-in [:errors league-id] (ex-message e))
            (println (str "[" (name (:provider cfg)) "]") (name league-id) "-" (ex-message e))
            (when (= :budget (:type (ex-data e))) (throw e)))
          (catch Exception e
            (swap! state assoc-in [:errors league-id] (str "Sync failed: " (ex-message e)))
            (println (str "[" (name (:provider cfg)) "]") (name league-id) "-" (ex-message e)))))
      (catch clojure.lang.ExceptionInfo _ nil))
    (let [all-failed? (= (count data/league-order) (count (:errors @state)))]
      (swap! state #(-> %
                        (assoc :last-sync (Instant/now))
                        (update :failed-passes (fn [n] (if all-failed? (inc (or n 0)) 0))))))
    (println (str "[" (name (:provider cfg)) "] sync done"
                  (when (= :api-football (:provider cfg))
                    (str "; requests left today: " (:day-remaining @api/limits "?")))))))

(defn- next-delay-ms
  "Normal interval, doubled after each pass where every league failed (max 24 h),
  so a plan or key problem doesn't burn a request quota."
  [refresh-minutes]
  (let [n (or (:failed-passes @state) 0)
        minutes (min (* 24 60) (* refresh-minutes (bit-shift-left 1 (min n 10))))]
    (when (pos? n)
      (println (str "[sync] all leagues failed; next attempt in " minutes " min")))
    (* minutes 60 1000)))

(defn start!
  "Loads the disk cache and starts the refresh loop for the configured provider."
  []
  (let [{:keys [provider season refresh-minutes] :as cfg} (config)]
    (swap! state assoc :config cfg)
    (if-not (enabled?)
      (println (str "[sync] Provider " (name provider) " isn't usable (missing API key?) - serving sample data."))
      (do
        (doseq [league-id data/league-order
                :let [cached (case provider
                               :api-football (af-load-cached season league-id)
                               (fd-load-cached season league-id))]
                :when cached]
          (swap! state assoc-in [:leagues league-id] cached))
        (println (str "[sync] Live data from " (name provider) " for season " season "/" (inc season)
                      ", refreshing every " refresh-minutes " min."))
        (when-not (:running? @state)
          (swap! state assoc :running? true)
          (doto (Thread. ^Runnable
                         (fn []
                           (loop []
                             (sync-all!)
                             (Thread/sleep (long (next-delay-ms refresh-minutes)))
                             (recur))))
            (.setDaemon true)
            (.setName "xpitch-sync")
            (.start)))))))

(defn league-data
  "Cached data for a league, or nil if nothing has been fetched yet."
  [league-id]
  (let [d (get-in @state [:leagues league-id])]
    (when (seq (:standings d)) d)))
