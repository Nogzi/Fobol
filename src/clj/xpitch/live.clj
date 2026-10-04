(ns xpitch.live
  "Turns cached API-Football data into the same shapes the sample data produces
  (standings rows, advanced rows, overview tiles, leaderboards). All pure functions."
  (:require [clojure.string :as str]
            [xpitch.stats :as stats]))

;; ---------- Parsing API responses ----------

(defn- record [m]
  {:p (get m "played") :w (get m "win") :d (get m "draw") :l (get m "lose")
   :gf (get-in m ["goals" "for"]) :ga (get-in m ["goals" "against"])})

(defn parse-standings
  "One row per team across all groups (a split league lists its groups in order)."
  [response]
  (->> (get-in (first response) ["league" "standings"])
       (apply concat)
       (mapv (fn [row]
               {:team-id (get-in row ["team" "id"])
                :name (get-in row ["team" "name"])
                :rank (get row "rank")
                :points (get row "points")
                :gd (get row "goalsDiff")
                :form (get row "form")
                :all (record (get row "all"))
                :home (record (get row "home"))
                :away (record (get row "away"))}))))

(defn parse-team-codes [response]
  (into {} (keep (fn [item]
                   (when-let [code (get-in item ["team" "code"])]
                     [(get-in item ["team" "id"]) code])))
        response))

(defn parse-fixtures [response]
  (mapv (fn [item]
          {:id (get-in item ["fixture" "id"])
           :date (get-in item ["fixture" "date"])
           :home-id (get-in item ["teams" "home" "id"])
           :away-id (get-in item ["teams" "away" "id"])
           :home-goals (get-in item ["goals" "home"])
           :away-goals (get-in item ["goals" "away"])})
        response))

(defn- stat-number
  "Stat values arrive as numbers, \"55%\", \"1.23\" or null."
  [v]
  (cond
    (number? v) (double v)
    (string? v) (some-> (re-find #"-?\d+(?:\.\d+)?" v) parse-double)
    :else nil))

(defn parse-fixture-stats
  "{fixture-id {team-id {:xg :shots :sot :poss}}} for fixtures that have statistics."
  [response]
  (into {}
        (keep (fn [item]
                (let [teams (get item "statistics")]
                  (when (= 2 (count teams))
                    [(get-in item ["fixture" "id"])
                     (into {} (map (fn [t]
                                     (let [by-type (into {} (map (juxt #(get % "type") #(stat-number (get % "value"))))
                                                         (get t "statistics"))]
                                       [(get-in t ["team" "id"])
                                        {:xg (by-type "expected_goals")
                                         :shots (by-type "Total Shots")
                                         :sot (by-type "Shots on Goal")
                                         :poss (by-type "Ball Possession")}])))
                           teams)]))))
        response))

;; ---------- Expected points ----------

(defn- poisson [lambda k]
  (/ (* (Math/exp (- lambda)) (Math/pow lambda k))
     (reduce * 1.0 (range 1 (inc k)))))

(defn expected-points
  "Points a team would expect from a match given both sides' xG (independent Poisson goals)."
  [xg-for xg-against]
  (let [goals (range 11)
        p-for (mapv #(poisson xg-for %) goals)
        p-against (mapv #(poisson xg-against %) goals)]
    (reduce + (for [i goals j goals
                    :let [p (* (p-for i) (p-against j))]]
                (cond (> i j) (* 3 p) (= i j) p :else 0)))))

;; ---------- Building views ----------

(defn- derive-code
  "Fallback when the API has no team code: first three letters, skipping prefixes like \"FC\"."
  [name]
  (let [letters (-> name
                    (str/replace #"^(FC|AC|AS|SC|SV|VfB|VfL|RB|RC|1\.)\s+" "")
                    (str/replace #"[^\p{L}]" ""))]
    (str/upper-case (subs letters 0 (min 3 (count letters))))))

(defn- team-code [codes {:keys [team-id name]}]
  (or (get codes team-id) (derive-code name)))

(defn- form-vec [form]
  (if (string? form)
    (mapv str (take-last 5 (str/replace form #"[^WDL]" "")))
    []))

(defn standings
  [league {:keys [standings codes]}]
  (->> standings
       (map-indexed (fn [i row]
                      (let [pos (inc i)
                            {:keys [p w d l]} (:all row)]
                        {:pos pos
                         :zone (stats/zone-for pos (:zones league))
                         :code (team-code codes row)
                         :name (:name row)
                         :short (or (:short row) (:name row))
                         :team-id (:team-id row)
                         :p p :w w :d d :l l
                         :gd (:gd row)
                         :pts (:points row)
                         :form (form-vec (:form row))})))
       vec))

(defn- team-matches
  "Each finished fixture with stats from one team's perspective, filtered by split."
  [fixtures fixture-stats team-id split]
  (for [{:keys [id home-id away-id] :as f} fixtures
        :let [home? (= team-id home-id)
              opponent (if home? away-id home-id)]
        :when (and (or home? (= team-id away-id))
                   (case split :overall true :home home? :away (not home?)))
        :let [s (get fixture-stats id)]
        :when s]
    (assoc f :own (get s team-id) :opp (get s opponent))))

(defn- avg [xs] (when (seq xs) (/ (reduce + xs) (count xs))))

(defn- match-x-pts
  "xG-based expected points when both sides have xG, otherwise a precomputed
  value (e.g. from betting odds)."
  [{:keys [own opp]}]
  (if (and (:xg own) (:xg opp))
    (expected-points (:xg own) (:xg opp))
    (:x-pts own)))

(defn- advanced-row [standing {:keys [fixtures fixture-stats]} split]
  (let [{:keys [p w d gf ga]} (get-in standing [:raw (case split :overall :all :home :home :away :away)])
        matches (team-matches fixtures fixture-stats (:team-id standing) split)
        with-xg (filter #(and (get-in % [:own :xg]) (get-in % [:opp :xg])) matches)
        ;; Totals are scaled from the matches that have stats up to all matches played.
        per-match-total (fn [xs] (when (pos? p) (some-> (avg xs) (* p) stats/round1)))
        xg (per-match-total (map #(get-in % [:own :xg]) with-xg))
        xga (per-match-total (map #(get-in % [:opp :xg]) with-xg))
        rate (fn [k] (some-> (avg (keep #(get-in % [:own k]) matches)) stats/round1))]
    (merge (select-keys standing [:pos :zone :code :name :short])
           {:p p
            :pts (if (= split :overall) (:pts standing) (+ (* 3 w) d))
            :gf gf
            :ga ga
            :x-pts (per-match-total (keep match-x-pts matches))
            :xg xg
            :xga xga
            :xgd (when (and xg xga) (stats/round1 (- xg xga)))
            :poss (rate :poss)
            :shg (rate :shots)
            :sotg (rate :sot)
            :covered (count with-xg)})))

(defn advanced [standing-rows raw-by-team data split]
  (mapv (fn [row] (advanced-row (assoc row :raw (raw-by-team (:team-id row))) data split))
        standing-rows))

(defn- leaderboards
  "The design's four boards when xG exists; otherwise boards built from goals,
  points and expected points so every league shows something."
  [rows x-pts-basis]
  (let [top (fn [metric fmt lower-better?]
              (let [rows (filter #(and (metric %) (pos? (:p %))) rows)
                    sorted (take 5 (sort-by metric (if lower-better? < >) rows))
                    best (some-> (first sorted) metric)]
                (mapv (fn [r] {:code (:code r)
                               :team (:short r)
                               :value (fmt (metric r))
                               :ratio (cond (nil? best) 0
                                            lower-better? (if (pos? (metric r)) (/ best (metric r)) 1)
                                            (pos? best) (/ (metric r) best)
                                            :else 0)})
                      sorted)))
        per-match (fn [k] #(some-> (k %) (/ (:p %))))
        has? (fn [k] (some k rows))]
    (if (has? :xg)
      [{:title "Expected goals" :subtitle "xG per match" :entries (top (per-match :xg) #(stats/fixed % 2) false)}
       (if (has? :poss)
         {:title "Possession" :subtitle "Average share of the ball" :entries (top :poss #(str (stats/fixed % 1) "%") false)}
         {:title "Shots on target" :subtitle "On-target attempts per match" :entries (top :sotg #(stats/fixed % 1) false)})
       {:title "Shots per game" :subtitle "Total attempts per match" :entries (top :shg #(stats/fixed % 1) false)}
       {:title "Best defence" :subtitle "xGA per match (lower is better)" :entries (top (per-match :xga) #(stats/fixed % 2) true)}]
      [{:title "Goals per game" :subtitle "Goals scored per match" :entries (top (per-match :gf) #(stats/fixed % 2) false)}
       {:title "Expected points" :subtitle (if (= x-pts-basis :odds) "xPts per match, from betting odds" "xPts per match")
        :entries (top (per-match :x-pts) #(stats/fixed % 2) false)}
       {:title "Points per game" :subtitle "Average points per match" :entries (top (per-match :pts) #(stats/fixed % 2) false)}
       {:title "Best defence" :subtitle "Goals conceded per match (lower is better)" :entries (top (per-match :ga) #(stats/fixed % 2) true)}])))

(defn- overview [{:keys [fixtures fixture-stats]}]
  (let [n (count fixtures)
        with-stats (keep #(when-let [s (get fixture-stats (:id %))] (vals s)) fixtures)
        with-xg (filter #(every? :xg %) with-stats)
        xgpm (avg (map #(reduce + (map :xg %)) with-xg))
        goals-of #(+ (or (:home-goals %) 0) (or (:away-goals %) 0))
        gpm (avg (map goals-of fixtures))
        covered-goals (avg (keep #(when-let [s (get fixture-stats (:id %))]
                                    (when (every? :xg (vals s)) (goals-of %)))
                                 fixtures))
        shots (avg (keep #(when (every? :shots %) (reduce + (map :shots %))) with-stats))
        sot (avg (keep #(when (every? :sot %) (reduce + (map :sot %))) with-stats))
        share (fn [pred] (when (pos? n) (Math/round (* 100.0 (/ (count (filter pred fixtures)) n)))))
        home-win (share #(> (or (:home-goals %) 0) (or (:away-goals %) 0)))
        draws (share #(= (:home-goals %) (:away-goals %)))
        dash "–"]
    [{:label "xG / match" :value (if xgpm (stats/fixed xgpm 2) dash)
      :caption (if xgpm "Expected goals, both teams combined" "Not available for this league")
      :ratio (if xgpm (/ xgpm 4.4) 0)}
     {:label "Goals / match" :value (if gpm (stats/fixed gpm 2) dash)
      :caption (if (and xgpm covered-goals (pos? xgpm))
                 (let [diff (* 100 (- (/ covered-goals xgpm) 1))]
                   (format "Scoring %s%% %s expected" (stats/fixed (abs diff) 1) (if (>= diff 0) "above" "below")))
                 (str n " matches played"))
      :ratio (if gpm (/ gpm 4.3) 0)}
     {:label "Shots / match" :value (if shots (stats/fixed shots 1) dash)
      :caption (if sot (str (stats/fixed sot 1) " on target per match") "Not available for this league")
      :ratio (if shots (/ shots 46.5) 0)}
     {:label "Home win rate" :value (if home-win (str home-win "%") dash)
      :caption (if home-win (format "Draws %d%% · Away wins %d%%" draws (- 100 home-win draws)) "No matches yet")
      :ratio (if home-win (/ home-win 100.0) 0)}]))

(defn league-view
  "Everything the API serves for one league, built from its cached API data."
  [league data]
  (let [rows (standings league data)
        raw-by-team (into {} (map (juxt :team-id identity)) (:standings data))
        adv (into {} (for [split [:overall :home :away]]
                       [split (advanced rows raw-by-team data split)]))
        finished (count (:fixtures data))
        covered (count (filter #(contains? (:fixture-stats data) (:id %)) (:fixtures data)))
        x-pts-basis (or (:x-pts-basis data) :xg)]
    {:standings (mapv #(dissoc % :team-id) rows)
     :matchday (reduce max 0 (map :p rows))
     :matches-played finished
     :overview (overview data)
     :leaderboards (leaderboards (:overall adv) x-pts-basis)
     :advanced (update-vals adv (fn [rs] (mapv #(dissoc % :covered :gf :ga) rs)))
     :stats-coverage {:covered covered :finished finished}
     :x-pts-basis x-pts-basis
     :last-match (:last-match data)}))
