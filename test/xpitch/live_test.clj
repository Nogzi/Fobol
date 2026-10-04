(ns xpitch.live-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json]
            [xpitch.data :as data]
            [xpitch.live :as live]))

(defn- rec [p w d l gf ga]
  {"played" p "win" w "draw" d "lose" l "goals" {"for" gf "against" ga}})

(def standings-json
  ;; Shaped like GET /standings: response[0].league.standings is a list of groups.
  [{"league" {"id" 39
              "standings" [[{"rank" 1 "team" {"id" 42 "name" "Arsenal"} "points" 4 "goalsDiff" 2 "form" "DW"
                             "all" (rec 2 1 1 0 3 1) "home" (rec 1 1 0 0 2 0) "away" (rec 1 0 1 0 1 1)}
                            {"rank" 2 "team" {"id" 40 "name" "Liverpool"} "points" 1 "goalsDiff" 0 "form" "D"
                             "all" (rec 1 0 1 0 1 1) "home" (rec 1 0 1 0 1 1) "away" (rec 0 0 0 0 0 0)}
                            {"rank" 3 "team" {"id" 33 "name" "Manchester United"} "points" 0 "goalsDiff" -2 "form" "L"
                             "all" (rec 1 0 0 1 0 2) "home" (rec 0 0 0 0 0 0) "away" (rec 1 0 0 1 0 2)}]]}}])

(def teams-json
  [{"team" {"id" 42 "name" "Arsenal" "code" "ARS"}}
   {"team" {"id" 40 "name" "Liverpool" "code" "LIV"}}
   {"team" {"id" 33 "name" "Manchester United" "code" nil}}])

(def fixtures-json
  [{"fixture" {"id" 1 "date" "2026-08-16T14:00:00+00:00"} "teams" {"home" {"id" 42} "away" {"id" 33}} "goals" {"home" 2 "away" 0}}
   {"fixture" {"id" 2 "date" "2026-08-23T14:00:00+00:00"} "teams" {"home" {"id" 40} "away" {"id" 42}} "goals" {"home" 1 "away" 1}}])

(defn- team-stats [id xg shots sot poss]
  {"team" {"id" id}
   "statistics" [{"type" "Shots on Goal" "value" sot}
                 {"type" "Total Shots" "value" shots}
                 {"type" "Ball Possession" "value" poss}
                 {"type" "expected_goals" "value" xg}]})

(def fixture-stats-json
  ;; Shaped like GET /fixtures?ids=… — each item carries its statistics.
  [{"fixture" {"id" 1} "statistics" [(team-stats 42 "2.10" 18 7 "61%") (team-stats 33 "0.45" 6 1 "39%")]}
   {"fixture" {"id" 2} "statistics" [(team-stats 40 "1.30" 12 4 "52%") (team-stats 42 "1.10" 10 3 "48%")]}
   {"fixture" {"id" 3} "statistics" []}])

;; Round-trip through jsonista so values have the same types as real responses.
(defn- json-like [x] (json/read-value (json/write-value-as-string x)))

(def cached
  {:standings (live/parse-standings (json-like standings-json))
   :codes (live/parse-team-codes (json-like teams-json))
   :fixtures (live/parse-fixtures (json-like fixtures-json))
   :fixture-stats (live/parse-fixture-stats (json-like fixture-stats-json))})

(deftest parsing
  (is (= {42 "ARS" 40 "LIV"} (:codes cached)) "null codes are skipped")
  (is (= {:xg 2.1 :shots 18.0 :sot 7.0 :poss 61.0} (get-in cached [:fixture-stats 1 42])))
  (is (not (contains? (:fixture-stats cached) 3)) "fixtures without statistics are left for a later sync"))

(deftest expected-points
  (is (< 2.0 (live/expected-points 2.1 0.45) 3.0))
  (is (< (live/expected-points 0.45 2.1) 0.5))
  (testing "two evenly matched sides share roughly 2.6 points"
    (let [total (+ (live/expected-points 1.3 1.3) (live/expected-points 1.3 1.3))]
      (is (< 2.4 total 2.8)))))

(deftest league-view
  (let [view (live/league-view (data/leagues :premier-league) cached)
        [ars liv mun] (:standings view)]
    (is (= ["ARS" "LIV" "MAN"] (map :code (:standings view))) "missing code falls back to the name")
    (is (= [:ucl :ucl :ucl] (map :zone (:standings view))))
    (is (= ["D" "W"] (:form ars)))
    (is (= {:p 2 :pts 4} (select-keys ars [:p :pts])))
    (is (= 2 (:matchday view)))
    (testing "advanced totals come from match xG"
      (let [ars-adv (first (get-in view [:advanced :overall]))]
        (is (= 3.2 (:xg ars-adv)))
        (is (= 1.8 (:xga ars-adv)))
        (is (= 1.4 (:xgd ars-adv)))
        (is (= 54.5 (:poss ars-adv)))
        (is (= 14.0 (:shg ars-adv)))))
    (testing "home/away splits use each team's own matches"
      (let [home (first (get-in view [:advanced :home]))
            away (first (get-in view [:advanced :away]))]
        (is (= [1 3 2.1] ((juxt :p :pts :xg) home)))
        (is (= [1 1 1.1] ((juxt :p :pts :xg) away)))))
    (testing "a team with no away matches has no away stats"
      (let [liv-away (second (get-in view [:advanced :away]))]
        (is (= 0 (:p liv-away)))
        (is (nil? (:xg liv-away)))))
    (is (= "ARS" (-> view :leaderboards first :entries first :code)))
    (is (= "2.00" (-> view :overview second :value)) "goals per match")
    (is (= {:covered 2 :finished 2} (:stats-coverage view)))
    (is (= 3 (count (filter :name [ars liv mun]))))))
