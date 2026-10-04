(ns xpitch.stats
  "Standings, advanced stats, home/away splits, leaderboards and overview tiles."
  (:require [xpitch.data :as data])
  (:import (java.text Collator)
           (java.util Locale)))

(defn seed-rand
  "Deterministic PRNG seeded from a string (32-bit LCG), so sample data is stable."
  [^String seed]
  (let [state (volatile! (reduce (fn [h ch] (bit-and (+ (* h 31) (int ch)) 0xFFFFFFFF)) 0 seed))]
    (fn []
      (vswap! state #(bit-and (+ (* % 1664525) 1013904223) 0xFFFFFFFF))
      (/ (double @state) 4294967296.0))))

(defn round1 [x] (/ (Math/round (* (double x) 10.0)) 10.0))
(defn- clamp [x lo hi] (min hi (max lo x)))
(defn fixed [x digits] (format (str "%." digits "f") (double x)))
(defn- signed [x digits] (str (if (neg? x) "−" "+") (fixed (abs x) digits)))

(defn zone-for [pos zones]
  (or (some (fn [[zone [from to]]] (when (<= from pos to) zone)) zones)
      :none))

(defn- form-for [code w d l]
  (let [rand (seed-rand code)
        total (+ w d l)]
    (vec (repeatedly 5 #(let [x (* (rand) total)]
                          (cond (< x w) "W" (< x (+ w d)) "D" :else "L"))))))

(def ^:private ^Collator collator (Collator/getInstance Locale/ENGLISH))

(defn- standings* [league-id]
  (let [{:keys [teams zones]} (data/leagues league-id)]
    (->> teams
         (map #(assoc % :p (+ (:w %) (:d %) (:l %)) :pts (+ (* 3 (:w %)) (:d %))))
         (sort (fn [a b]
                 (let [c (compare [(:pts b) (:gd b)] [(:pts a) (:gd a)])]
                   (if (zero? c) (.compare collator (:name a) (:name b)) c))))
         (map-indexed (fn [i t]
                        (let [pos (inc i)]
                          (assoc t :pos pos
                                   :zone (zone-for pos zones)
                                   :form (form-for (:code t) (:w t) (:d t) (:l t))))))
         vec)))

(def standings
  "Current league table, sorted by points, goal difference, then name."
  (memoize standings*))

(defn matchday [league-id] (:p (first (standings league-id))))

(defn matches-played [league-id]
  (quot (reduce + (map :p (standings league-id))) 2))

(defn- derive-advanced
  "Generates plausible advanced stats from a team's points and goal difference."
  [league-id {:keys [code p pts gd]}]
  (let [rand (seed-rand (str (name league-id) code))
        noise #(* (- (* (rand) 2) 1) %)
        ppg (/ pts p 3.0)
        gdpg (clamp (/ (+ (/ gd (double p)) 2) 4) 0 1)
        s (clamp (+ (* 0.6 ppg) (* 0.4 gdpg) (noise 0.06)) 0 1)
        shg (+ 8.5 (* 9 s) (noise 0.8))
        x-pts (round1 (+ (* p (+ 0.6 (* 1.8 s))) (noise 0.5)))
        xg (round1 (* p (+ 0.8 (* 1.4 s) (noise 0.12))))
        xga (round1 (* p (+ (- 2.0 (* 1.2 s)) (noise 0.12))))
        poss (round1 (+ 40 (* 22 s) (noise 2.5)))]
    {:x-pts x-pts :xg xg :xga xga :poss poss
     :shg (round1 shg)
     :sotg (round1 (+ (* shg 0.35) (noise 0.3)))}))

(defn- overall-advanced* [league-id]
  (mapv (fn [row]
          (let [stats (if-let [[x-pts xg xga poss shg sotg] (and (= league-id :premier-league)
                                                                  (data/pl-advanced (:code row)))]
                        {:x-pts x-pts :xg xg :xga xga :poss poss :shg shg :sotg sotg}
                        (derive-advanced league-id row))]
            (merge (select-keys row [:pos :zone :code :name :short :p :pts])
                   stats
                   {:xgd (round1 (- (:xg stats) (:xga stats)))})))
        (standings league-id)))

(def overall-advanced (memoize overall-advanced*))

(defn split-advanced
  "Splits season totals into :home or :away. Per-match rates shift slightly toward the home side."
  [row split]
  (let [home? (= split :home)
        {:keys [p pts]} row
        p-home (long (Math/ceil (/ p 2.0)))
        p-away (- p p-home)
        home-pts (let [hp (min (* 3 p-home) (Math/round (* pts 0.58)))]
                   (if (> (- pts hp) (* 3 p-away)) (- pts (* 3 p-away)) hp))
        share (fn [total home-share] (round1 (* total (if home? home-share (- 1 home-share)))))
        xg (share (:xg row) 0.56)
        xga (share (:xga row) 0.44)
        rate (if home? 1.08 0.92)]
    (assoc row
           :p (if home? p-home p-away)
           :pts (if home? home-pts (- pts home-pts))
           :x-pts (share (:x-pts row) 0.56)
           :xg xg
           :xga xga
           :xgd (round1 (- xg xga))
           :poss (round1 (clamp (+ (:poss row) (if home? 1.8 -1.8)) 0 100))
           :shg (round1 (* (:shg row) rate))
           :sotg (round1 (* (:sotg row) rate)))))

(defn advanced
  "Advanced standings for :overall, :home or :away."
  [league-id split]
  (let [rows (overall-advanced league-id)]
    (if (= split :overall) rows (mapv #(split-advanced % split) rows))))

(defn leaderboards [league-id]
  (let [rows (overall-advanced league-id)
        top (fn [metric fmt lower-better?]
              (let [sorted (take 5 (sort-by metric (if lower-better? < >) rows))
                    best (metric (first sorted))]
                (mapv (fn [r] {:code (:code r)
                               :team (:short r)
                               :value (fmt (metric r))
                               :ratio (if lower-better? (/ best (metric r)) (/ (metric r) best))})
                      sorted)))]
    [{:title "Expected goals" :subtitle "xG per match"
      :entries (top #(/ (:xg %) (:p %)) #(fixed % 2) false)}
     {:title "Possession" :subtitle "Average share of the ball"
      :entries (top :poss #(str (fixed % 1) "%") false)}
     {:title "Shots per game" :subtitle "Total attempts per match"
      :entries (top :shg #(fixed % 1) false)}
     {:title "Best defence" :subtitle "xGA per match (lower is better)"
      :entries (top #(/ (:xga %) (:p %)) #(fixed % 2) true)}]))

(defn overview [league-id]
  (if (= league-id :premier-league)
    data/pl-overview
    (let [rows (overall-advanced league-id)
          rand (seed-rand (str (name league-id) "-overview"))
          matches (/ (reduce + (map :p rows)) 2.0)
          xgpm (/ (reduce + (map :xg rows)) matches)
          overperf (- (* (rand) 0.12) 0.04)
          gpm (* xgpm (+ 1 overperf))
          shots (/ (reduce + (map #(* (:shg %) (:p %)) rows)) matches)
          on-target (/ (reduce + (map #(* (:sotg %) (:p %)) rows)) matches)
          home-win (Math/round (+ 40 (* (rand) 8)))
          draws (Math/round (+ 22 (* (rand) 6)))
          delta #(round1 (* (- (* (rand) 2) 1) %))
          dx (delta 0.15)
          dg (delta 0.2)
          ds (delta 1.2)
          dh (Math/round (* (- (* (rand) 2) 1) 4))]
      [{:label "xG / match" :value (fixed xgpm 2) :delta (str (signed dx 2) " vs 25/26") :delta-positive? (>= dx 0)
        :caption "Expected goals, both teams combined" :ratio (/ xgpm 4.4)}
       {:label "Goals / match" :value (fixed gpm 2) :delta (str (signed dg 2) " vs 25/26") :delta-positive? (>= dg 0)
        :caption (format "Scoring %s%% %s expected" (fixed (abs (* overperf 100)) 1) (if (>= overperf 0) "above" "below"))
        :ratio (/ gpm 4.3)}
       {:label "Shots / match" :value (fixed shots 1) :delta (str (signed ds 1) " vs 25/26") :delta-positive? (>= ds 0)
        :caption (str (fixed on-target 1) " on target per match") :ratio (/ shots 46.5)}
       {:label "Home win rate" :value (str home-win "%")
        :delta (str (if (neg? dh) "−" "+") (abs dh) " pp vs 25/26") :delta-positive? (>= dh 0)
        :caption (format "Draws %d%% · Away wins %d%%" draws (- 100 home-win draws)) :ratio (/ home-win 100.0)}])))

(defn league-summary [league-id]
  (let [league (data/leagues league-id)]
    (-> (select-keys league [:id :name :country :badge :total-matchdays])
        (assoc :team-count (count (:teams league))
               :matchday (matchday league-id)
               :matches-played (matches-played league-id)))))

(defn league-detail [league-id]
  (assoc (league-summary league-id)
         :overview (overview league-id)
         :leaderboards (leaderboards league-id)
         :advanced {:overall (advanced league-id :overall)
                    :home (advanced league-id :home)
                    :away (advanced league-id :away)}))
