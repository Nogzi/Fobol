(ns xpitch.football-data
  "football-data.co.uk CSVs → the cached league-data shape used by xpitch.live.
  Main leagues have xG and shots per match; the Danish file has results and odds only,
  so its xPts comes from closing betting odds."
  (:require [clojure.data.csv :as csv]
            [clojure.string :as str]
            [xpitch.teams :as teams])
  (:import (java.net URI)
           (java.net.http HttpClient HttpClient$Redirect HttpRequest HttpResponse$BodyHandlers)
           (java.time Duration)))

(def league-files
  {:premier-league {:file "E0"} :la-liga {:file "SP1"} :bundesliga {:file "D1"}
   :serie-a {:file "I1"} :ligue-1 {:file "F1"} :superliga {:extra "DNK"}})

(defn csv-url [league-id season]
  (let [{:keys [file extra]} (league-files league-id)
        yy #(format "%02d" (mod % 100))]
    (if extra
      (str "https://football-data.co.uk/new/" extra ".csv")
      (str "https://football-data.co.uk/mmz4281/" (yy season) (yy (inc season)) "/" file ".csv"))))

(defonce ^:private client
  (delay (-> (HttpClient/newBuilder)
             (.connectTimeout (Duration/ofSeconds 10))
             (.followRedirects HttpClient$Redirect/NORMAL)
             .build)))

(defn fetch-csv! [url]
  (let [request (-> (HttpRequest/newBuilder (URI/create url)) (.timeout (Duration/ofSeconds 60)) .GET .build)
        response (.send @client request (HttpResponse$BodyHandlers/ofString))]
    (when-not (= 200 (.statusCode response))
      (throw (ex-info (str "football-data.co.uk HTTP " (.statusCode response) " for " url)
                      {:type :http :status (.statusCode response)})))
    (.body response)))

;; ---------- Parsing ----------

(defn- rows->maps [text]
  (let [[header & rows] (csv/read-csv (str/replace text #"^﻿" ""))
        header (mapv str/trim header)]
    (keep (fn [row] (when (> (count row) 3) (zipmap header row))) rows)))

(defn- parse-num [s] (some-> s str/trim not-empty parse-double))
(defn- int-num [s] (some-> (parse-num s) long))

(defn- iso-date
  "dd/mm/yyyy (or dd/mm/yy) + HH:mm → sortable \"yyyy-mm-ddTHH:mm\"."
  [date time]
  (when-let [[_ d m y] (re-find #"(\d{1,2})/(\d{1,2})/(\d{2,4})" (or date ""))]
    (format "%s-%02d-%02dT%s"
            (if (= 2 (count y)) (str "20" y) y) (parse-long m) (parse-long d)
            (or (not-empty (str/trim (or time ""))) "00:00"))))

(defn- odds-probabilities
  "Implied home/draw/away probabilities with the bookmaker margin removed.
  Prefers market-average closing odds."
  [row]
  (some (fn [[h d a]]
          (let [odds (map #(parse-num (get row %)) [h d a])]
            (when (every? #(and % (> % 1.0)) odds)
              (let [inv (map #(/ 1.0 %) odds)
                    total (reduce + inv)]
                (mapv #(/ % total) inv)))))
        [["AvgCH" "AvgCD" "AvgCA"] ["AvgH" "AvgD" "AvgA"] ["B365CH" "B365CD" "B365CA"] ["B365H" "B365D" "B365A"]
         ["PSCH" "PSCD" "PSCA"] ["MaxCH" "MaxCD" "MaxCA"]]))

(defn parse-matches
  "Finished matches for the season, oldest first."
  [text {:keys [extra-season]}]
  (->> (rows->maps text)
       (filter #(or (nil? extra-season) (= extra-season (get % "Season"))))
       (keep (fn [row]
               (let [home (or (not-empty (get row "HomeTeam")) (get row "Home"))
                     away (or (not-empty (get row "AwayTeam")) (get row "Away"))
                     hg (int-num (or (get row "FTHG") (get row "HG")))
                     ag (int-num (or (get row "FTAG") (get row "AG")))]
                 (when (and home away hg ag)
                   (let [[ph pd pa] (odds-probabilities row)]
                     {:date (iso-date (get row "Date") (get row "Time"))
                      :home home :away away :home-goals hg :away-goals ag
                      :home-xg (parse-num (get row "HxG")) :away-xg (parse-num (get row "AxG"))
                      :home-shots (parse-num (get row "HS")) :away-shots (parse-num (get row "AS"))
                      :home-sot (parse-num (get row "HST")) :away-sot (parse-num (get row "AST"))
                      :odds-xpts (when ph {:home (+ (* 3 ph) pd) :away (+ (* 3 pa) pd)})})))))
       (sort-by :date)
       vec))

;; ---------- League data ----------

(def ^:private empty-record {:p 0 :w 0 :d 0 :l 0 :gf 0 :ga 0})

(defn- add-result [rec gf ga]
  (-> rec
      (update :p inc)
      (update (cond (> gf ga) :w (= gf ga) :d :else :l) inc)
      (update :gf + gf)
      (update :ga + ga)))

(defn- result-letter [gf ga] (cond (> gf ga) "W" (= gf ga) "D" :else "L"))

(defn standings
  "API-style standings rows computed from results.
  Tie-break: points, goal difference, goals scored, name (leagues' head-to-head rules aren't applied)."
  [matches]
  (let [teams (reduce (fn [acc {:keys [home away home-goals away-goals]}]
                        (-> acc
                            (update-in [home :all] (fnil add-result empty-record) home-goals away-goals)
                            (update-in [home :home] (fnil add-result empty-record) home-goals away-goals)
                            (update-in [home :form] (fnil conj []) (result-letter home-goals away-goals))
                            (update-in [away :all] (fnil add-result empty-record) away-goals home-goals)
                            (update-in [away :away] (fnil add-result empty-record) away-goals home-goals)
                            (update-in [away :form] (fnil conj []) (result-letter away-goals home-goals))))
                      {} matches)]
    (->> teams
         (map (fn [[team {:keys [all home away form]}]]
                (let [{:keys [name short]} (teams/lookup team)]
                  {:team-id team :name name :short short
                   :points (+ (* 3 (:w all)) (:d all))
                   :gd (- (:gf all) (:ga all))
                   :form (apply str (take-last 5 form))
                   :all all
                   :home (or home empty-record)
                   :away (or away empty-record)})))
         (sort-by (juxt (comp - :points) (comp - :gd) (comp - :gf :all) :name))
         (map-indexed (fn [i row] (assoc row :rank (inc i))))
         vec)))

(defn league-data
  "Cached-league shape consumed by xpitch.live/league-view."
  [matches]
  (let [fixtures (mapv (fn [{:keys [date home away] :as m}]
                         {:id (str date "|" home "|" away)
                          :date date :home-id home :away-id away
                          :home-goals (:home-goals m) :away-goals (:away-goals m)})
                       matches)
        stats (into {} (map (fn [{:keys [date home away] :as m}]
                              [(str date "|" home "|" away)
                               {home {:xg (:home-xg m) :shots (:home-shots m) :sot (:home-sot m)
                                      :x-pts (get-in m [:odds-xpts :home])}
                                away {:xg (:away-xg m) :shots (:away-shots m) :sot (:away-sot m)
                                      :x-pts (get-in m [:odds-xpts :away])}}]))
                    matches)
        rows (standings matches)]
    {:standings rows
     :codes (into {} (keep (fn [{:keys [team-id]}]
                             (when-let [code (:code (teams/lookup team-id))] [team-id code])))
                  rows)
     :fixtures fixtures
     :fixture-stats stats
     :x-pts-basis (if (some :home-xg matches) :xg :odds)
     :last-match (:date (peek matches))}))

(defn load-league
  "Parses a league's CSV text for `season` (start year)."
  [league-id season text]
  (let [extra? (:extra (league-files league-id))]
    (league-data (parse-matches text {:extra-season (when extra? (str season "/" (inc season)))}))))
