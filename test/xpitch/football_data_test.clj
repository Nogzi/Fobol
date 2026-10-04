(ns xpitch.football-data-test
  (:require [clojure.test :refer [deftest is testing]]
            [xpitch.data :as data]
            [xpitch.football-data :as fd]
            [xpitch.live :as live]))

;; Trimmed to the columns we read, in the same layout as the real files (BOM included).
(def main-csv
  (str "﻿Div,Date,Time,HomeTeam,AwayTeam,FTHG,FTAG,FTR,HxG,AxG,HS,AS,HST,AST,AvgCH,AvgCD,AvgCA\n"
       "E0,21/08/2026,20:00,Arsenal,Coventry,3,0,H,1.88,0.2,20,4,6,1,1.2,6.72,14\n"
       "E0,22/08/2026,12:30,Hull,Man United,2,0,H,1.01,1.83,8,21,4,5,8.49,5.11,1.34\n"
       "E0,29/08/2026,15:00,Man United,Arsenal,1,1,D,1.4,1.6,12,14,5,6,3.1,3.5,2.3\n"))

(def dnk-csv
  (str "﻿Country,League,Season,Date,Time,Home,Away,HG,AG,Res,PSCH,PSCD,PSCA,AvgCH,AvgCD,AvgCA\n"
       "Denmark,Superliga,2025/2026,10/05/2026,15:00,Odense,Viborg,1,0,H,,,,2.1,3.4,3.3\n"
       "Denmark,Superliga,2026/2027,20/09/2026,13:00,Sonderjyske,Randers FC,4,2,H,,,,2.69,3.46,2.33\n"
       "Denmark,Superliga,2026/2027,20/09/2026,15:00,Horsens,Aarhus,2,2,D,,,,3.9,3.66,1.79\n"))

(deftest urls
  (is (= "https://football-data.co.uk/mmz4281/2627/E0.csv" (fd/csv-url :premier-league 2026)))
  (is (= "https://football-data.co.uk/mmz4281/9900/D1.csv" (fd/csv-url :bundesliga 1999)))
  (is (= "https://football-data.co.uk/new/DNK.csv" (fd/csv-url :superliga 2026))))

(deftest parse-main-league
  (let [matches (fd/parse-matches main-csv {})]
    (is (= 3 (count matches)))
    (is (= {:date "2026-08-21T20:00" :home "Arsenal" :away "Coventry" :home-goals 3 :away-goals 0
            :home-xg 1.88 :away-xg 0.2 :home-shots 20.0 :away-shots 4.0 :home-sot 6.0 :away-sot 1.0}
           (dissoc (first matches) :odds-xpts)))
    (testing "odds become margin-free expected points"
      (let [{:keys [home away]} (:odds-xpts (first matches))]
        (is (< 2.4 home 2.7))
        (is (< 0.1 away 0.4))))))

(deftest parse-extra-league-filters-season
  (let [matches (fd/parse-matches dnk-csv {:extra-season "2026/2027"})]
    (is (= ["Sonderjyske" "Horsens"] (map :home matches)))
    (is (nil? (:home-xg (first matches))))
    (is (some? (:odds-xpts (first matches))))))

(deftest standings-from-results
  (let [rows (fd/standings (fd/parse-matches main-csv {}))]
    (is (= ["Arsenal" "Hull City" "Manchester United" "Coventry City"] (map :name rows)))
    (is (= [4 3 1 0] (map :points rows)))
    (is (= "WD" (:form (first rows))) "oldest first")
    (is (= {:p 1 :w 0 :d 1 :l 0 :gf 1 :ga 1} (:home (nth rows 2))))
    (is (= {:p 1 :w 0 :d 0 :l 1 :gf 0 :ga 2} (:away (nth rows 2))))))

(deftest league-view-main-uses-xg
  (let [view (live/league-view (data/leagues :premier-league) (fd/league-data (fd/parse-matches main-csv {})))
        ars (first (get-in view [:advanced :overall]))]
    (is (= :xg (:x-pts-basis view)))
    (is (= ["ARS" "HUL" "MUN" "COV"] (map :code (:standings view))))
    (is (= 3.5 (:xg ars)) "1.88 + 1.6")
    (is (nil? (:poss ars)) "football-data has no possession")
    (is (= "Expected goals" (-> view :leaderboards first :title)))
    (is (= "Shots on target" (-> view :leaderboards second :title)) "replaces possession")))

(deftest league-view-superliga-uses-odds
  (let [view (live/league-view (data/leagues :superliga)
                               (fd/load-league :superliga 2026 dnk-csv))
        rows (get-in view [:advanced :overall])]
    (is (= :odds (:x-pts-basis view)))
    (is (every? :x-pts rows) "every team gets odds-based xPts")
    (is (every? (comp nil? :xg) rows))
    (is (= "Goals per game" (-> view :leaderboards first :title)))
    (is (= "SønderjyskE" (:name (first (:standings view)))))))
