(ns xpitch.source
  "Chooses live data or sample data for each league, and shapes API payloads."
  (:require [xpitch.data :as data]
            [xpitch.live :as live]
            [xpitch.stats :as stats]
            [xpitch.sync :as sync]))

(def ^:private provider-names
  {:football-data "football-data.co.uk" :api-football "API-Football"})

(defn- meta-fields [league-id]
  (let [league (data/leagues league-id)]
    (select-keys league [:id :name :country :badge :total-matchdays])))

(defn- sample-view [league-id]
  {:source :sample
   :standings (stats/standings league-id)
   :team-count (count (get-in data/leagues [league-id :teams]))
   :matchday (stats/matchday league-id)
   :matches-played (stats/matches-played league-id)
   :x-pts-basis :xg})

(defn- live-view [league-id cached]
  (let [view (live/league-view (data/leagues league-id) cached)]
    (assoc view :source :live :team-count (count (:standings view)))))

(defn- league-note
  "Explains why a league isn't live, or what its advanced stats are based on."
  [league-id view]
  (let [error (get-in @sync/state [:errors league-id])
        {:keys [covered finished]} (:stats-coverage view)]
    (cond
      (and (sync/enabled?) (= :sample (:source view)))
      (str "Live data not loaded yet" (when error (str " (" error ")")) " — showing sample data.")
      (and (= :live (:source view)) (= :odds (:x-pts-basis view)))
      "xG, shots and possession aren't available for this league; xPts is calculated from pre-match betting odds."
      (and (= :live (:source view)) (pos? (or finished 0)) (< covered finished))
      (str "Advanced stats are based on " covered " of " finished
           " finished matches and scaled to matches played; more are fetched on each sync.")
      :else nil)))

(defn- view [league-id]
  (if-let [cached (and (sync/enabled?) (sync/league-data league-id))]
    (live-view league-id cached)
    (sample-view league-id)))

(defn leagues-payload []
  (let [views (into {} (map (juxt identity view)) data/league-order)
        leagues (mapv (fn [id]
                        (let [v (views id)]
                          (merge (meta-fields id)
                                 (select-keys v [:source :standings :team-count :matchday :matches-played])
                                 {:note (league-note id v)})))
                      data/league-order)
        live? (some #(= :live (:source %)) leagues)]
    {:mode (if live? :live :sample)
     :provider (when live? (provider-names (sync/provider)))
     :updated-at (when live? (some-> (:last-sync @sync/state) str))
     :data-through (when live? (some->> (vals views) (keep :last-match) seq (reduce #(if (pos? (compare %1 %2)) %1 %2))))
     :leagues leagues}))

(defn league-payload [league-id]
  (let [v (view league-id)
        detail (if (= :live (:source v))
                 v
                 (merge v (select-keys (stats/league-detail league-id) [:overview :leaderboards :advanced])))]
    (merge (meta-fields league-id)
           (select-keys detail [:source :team-count :matchday :matches-played :overview :leaderboards :advanced
                                :x-pts-basis :last-match])
           {:note (league-note league-id v)
            :provider (when (= :live (:source v)) (provider-names (sync/provider)))})))
