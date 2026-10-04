(ns xpitch.stats-test
  (:require [clojure.test :refer [deftest is testing]]
            [xpitch.data :as data]
            [xpitch.stats :as stats]))

(deftest standings-order
  (let [table (stats/standings :premier-league)]
    (is (= 20 (count table)))
    (is (= ["ARS" "LIV" "MCI"] (map :code (take 3 table))))
    (testing "ties on points and goal difference fall back to name"
      (is (= ["EVE" "FUL"] (map :code (subvec table 12 14)))))
    (testing "zones follow league rules"
      (is (= [:ucl :ucl :ucl :ucl :uel :uecl] (map :zone (take 6 table))))
      (is (= [:relegation :relegation :relegation] (map :zone (take-last 3 table)))))
    (is (every? #(= 5 (count (:form %))) table))))

(deftest every-league-has-consistent-tables
  (doseq [id data/league-order
          :let [table (stats/standings id)]]
    (is (= (map :pos table) (range 1 (inc (count table)))) (str id))
    (is (apply >= (map :pts table)) (str id))
    (is (zero? (reduce + (map :gd table))) (str id " goal differences sum to zero"))))

(deftest premier-league-uses-authored-stats
  (let [ars (first (stats/advanced :premier-league :overall))]
    (is (= {:code "ARS" :x-pts 14.8 :xg 14.6 :xga 5.2 :xgd 9.4}
           (select-keys ars [:code :x-pts :xg :xga :xgd])))))

(deftest home-away-splits-add-up
  (doseq [id data/league-order
          [overall home away] (map vector
                                   (stats/advanced id :overall)
                                   (stats/advanced id :home)
                                   (stats/advanced id :away))]
    (is (= (:p overall) (+ (:p home) (:p away))))
    (is (= (:pts overall) (+ (:pts home) (:pts away))))
    (is (<= (:pts home) (* 3 (:p home))))
    (is (<= (:pts away) (* 3 (:p away))))))

(deftest leaderboards-match-design
  (let [[xg poss _ defence] (stats/leaderboards :premier-league)]
    (is (= [["Man City" "2.19"] ["Arsenal" "2.09"] ["Liverpool" "2.01"] ["Chelsea" "1.89"] ["Newcastle" "1.73"]]
           (map (juxt :team :value) (:entries xg))))
    (is (= "63.5%" (-> poss :entries first :value)))
    (is (= ["Arsenal" "0.74"] ((juxt :team :value) (first (:entries defence)))))
    (is (every? #(<= 0 (:ratio %) 1) (mapcat :entries (stats/leaderboards :superliga))))))

(deftest derived-stats-are-deterministic
  (is (= (stats/overview :bundesliga) (stats/overview :bundesliga)))
  (is (= 4 (count (stats/overview :la-liga)))))
