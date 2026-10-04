(ns xpitch.data
  "Sample data mirroring the Figma design (Season 2026/27, early October).
  Club line-ups follow the 2025/26 leagues; all numbers are illustrative.")

(defn- teams
  "Rows are [code name W D L GD short-name?]."
  [rows]
  (mapv (fn [[code name w d l gd short]]
          {:code code :name name :short (or short name) :w w :d d :l l :gd gd})
        rows))

(def league-order
  [:premier-league :la-liga :bundesliga :serie-a :ligue-1 :superliga])

(def leagues
  {:premier-league
   {:id :premier-league :name "Premier League" :country "England" :badge "PL" :api-id 39 :total-matchdays 38
    :zones {:ucl [1 4] :uel [5 5] :uecl [6 6] :relegation [18 20]}
    :teams (teams [["ARS" "Arsenal" 5 1 1 10] ["LIV" "Liverpool" 5 0 2 6] ["MCI" "Manchester City" 4 2 1 9 "Man City"]
                   ["CHE" "Chelsea" 4 1 2 6] ["TOT" "Tottenham" 4 1 2 4] ["AVL" "Aston Villa" 3 3 1 3]
                   ["NEW" "Newcastle" 3 2 2 2] ["BHA" "Brighton" 3 2 2 1] ["MUN" "Manchester United" 3 1 3 0 "Man United"]
                   ["CRY" "Crystal Palace" 2 4 1 1] ["BOU" "Bournemouth" 3 1 3 -1] ["BRE" "Brentford" 3 0 4 -2]
                   ["FUL" "Fulham" 2 2 3 -2] ["NFO" "Nottingham Forest" 2 2 3 -3 "Nott'm Forest"] ["EVE" "Everton" 2 2 3 -2]
                   ["SUN" "Sunderland" 2 2 3 -3] ["LEE" "Leeds United" 2 1 4 -5] ["WHU" "West Ham" 1 2 4 -7]
                   ["BUR" "Burnley" 1 1 5 -8] ["WOL" "Wolves" 0 2 5 -9]])}

   :la-liga
   {:id :la-liga :name "La Liga" :country "Spain" :badge "LL" :api-id 140 :total-matchdays 38
    :zones {:ucl [1 4] :uel [5 6] :uecl [7 7] :relegation [18 20]}
    :teams (teams [["RMA" "Real Madrid" 6 0 1 12] ["BAR" "Barcelona" 5 1 1 13] ["ATM" "Atlético Madrid" 4 2 1 6]
                   ["VIL" "Villarreal" 4 2 1 5] ["BET" "Real Betis" 3 3 1 4] ["ATH" "Athletic Club" 3 2 2 1]
                   ["RSO" "Real Sociedad" 3 1 3 0] ["SEV" "Sevilla" 3 1 3 -1] ["CEL" "Celta Vigo" 2 3 2 0]
                   ["ESP" "Espanyol" 2 3 2 -1] ["GET" "Getafe" 2 2 3 -2] ["OSA" "Osasuna" 2 2 3 -2]
                   ["VAL" "Valencia" 2 2 3 -3] ["RAY" "Rayo Vallecano" 2 1 4 -3] ["ALA" "Alavés" 2 1 4 -4]
                   ["ELC" "Elche" 1 4 2 -2] ["MLL" "Mallorca" 1 3 3 -5] ["LEV" "Levante" 1 2 4 -5]
                   ["GIR" "Girona" 1 2 4 -6] ["OVI" "Real Oviedo" 0 3 4 -7]])}

   :bundesliga
   {:id :bundesliga :name "Bundesliga" :country "Germany" :badge "BL" :api-id 78 :total-matchdays 34
    :zones {:ucl [1 4] :uel [5 5] :uecl [6 6] :relegation [16 18]}
    :teams (teams [["FCB" "Bayern Munich" 6 0 0 17] ["BVB" "Borussia Dortmund" 4 2 0 8 "Dortmund"] ["RBL" "RB Leipzig" 4 1 1 3]
                   ["B04" "Bayer Leverkusen" 3 2 1 5 "Leverkusen"] ["VFB" "VfB Stuttgart" 4 0 2 2] ["SGE" "Eintracht Frankfurt" 3 1 2 2 "Frankfurt"]
                   ["TSG" "Hoffenheim" 3 1 2 1] ["KOE" "1. FC Köln" 3 1 2 1] ["SCF" "SC Freiburg" 2 2 2 0]
                   ["SVW" "Werder Bremen" 2 2 2 -2] ["FCU" "Union Berlin" 2 1 3 -3] ["HSV" "Hamburger SV" 2 1 3 -4]
                   ["M05" "Mainz 05" 1 2 3 -3] ["WOB" "VfL Wolfsburg" 1 2 3 -4] ["STP" "St. Pauli" 1 1 4 -5]
                   ["FCA" "FC Augsburg" 1 0 5 -7] ["BMG" "Gladbach" 0 3 3 -4] ["HDH" "Heidenheim" 0 2 4 -7]])}

   :serie-a
   {:id :serie-a :name "Serie A" :country "Italy" :badge "SA" :api-id 135 :total-matchdays 38
    :zones {:ucl [1 4] :uel [5 5] :uecl [6 6] :relegation [18 20]}
    :teams (teams [["NAP" "Napoli" 5 0 1 7] ["MIL" "AC Milan" 4 1 1 6] ["INT" "Inter" 4 0 2 8]
                   ["ROM" "Roma" 4 0 2 3] ["JUV" "Juventus" 3 3 0 4] ["COM" "Como" 3 2 1 3]
                   ["ATA" "Atalanta" 2 4 0 6] ["BOL" "Bologna" 3 1 2 3] ["LAZ" "Lazio" 2 2 2 2]
                   ["CRE" "Cremonese" 2 3 1 0] ["CAG" "Cagliari" 2 2 2 -1] ["UDI" "Udinese" 2 2 2 -2]
                   ["SAS" "Sassuolo" 2 1 3 -2] ["TOR" "Torino" 2 1 3 -6] ["PAR" "Parma" 1 2 3 -4]
                   ["FIO" "Fiorentina" 0 3 3 -4] ["GEN" "Genoa" 0 3 3 -5] ["LEC" "Lecce" 1 1 4 -6]
                   ["VER" "Hellas Verona" 0 3 3 -6] ["PIS" "Pisa" 0 3 3 -6]])}

   :ligue-1
   {:id :ligue-1 :name "Ligue 1" :country "France" :badge "L1" :api-id 61 :total-matchdays 34
    :zones {:ucl [1 4] :uel [5 5] :uecl [6 6] :relegation [16 18]}
    :teams (teams [["PSG" "Paris Saint-Germain" 5 1 1 10 "Paris SG"] ["OM" "Marseille" 5 0 2 10] ["OL" "Lyon" 5 0 2 5]
                   ["LOS" "Lille" 4 2 1 7] ["RCS" "Strasbourg" 4 1 2 6] ["ASM" "Monaco" 4 1 2 4]
                   ["REN" "Rennes" 3 3 1 1] ["RCL" "Lens" 3 2 2 2] ["TFC" "Toulouse" 3 2 2 2]
                   ["NIC" "Nice" 3 1 3 -1] ["PFC" "Paris FC" 2 2 3 -2] ["B29" "Brest" 2 2 3 -3]
                   ["LOR" "Lorient" 2 2 3 -6] ["AJA" "Auxerre" 2 1 4 -4] ["NAN" "Nantes" 1 3 3 -4]
                   ["ANG" "Angers" 1 3 3 -5] ["HAC" "Le Havre" 1 2 4 -8] ["FCM" "Metz" 0 2 5 -14]])}

   :superliga
   {:id :superliga :name "3F Superliga" :country "Denmark" :badge "SL" :api-id 119 :total-matchdays 22
    :zones {:ucl [1 1] :uel [2 2] :uecl [3 3] :relegation [11 12]}
    :teams (teams [["FCM" "FC Midtjylland" 8 2 1 16 "Midtjylland"] ["FCK" "FC København" 7 2 2 11 "København"] ["AGF" "AGF" 6 3 2 9]
                   ["BIF" "Brøndby IF" 6 2 3 6 "Brøndby"] ["FCN" "FC Nordsjælland" 5 2 4 3 "Nordsjælland"] ["RFC" "Randers FC" 4 3 4 0 "Randers"]
                   ["VFF" "Viborg FF" 4 2 5 -2 "Viborg"] ["SIF" "Silkeborg IF" 3 4 4 -3 "Silkeborg"] ["SJE" "SønderjyskE" 3 3 5 -6]
                   ["OB" "OB" 3 2 6 -7] ["FCF" "FC Fredericia" 2 3 6 -11 "Fredericia"] ["VB" "Vejle Boldklub" 1 3 7 -16 "Vejle"]])}})

(def pl-advanced
  "Hand-authored Premier League season totals: [xPts xG xGA poss% shots/g SoT/g]."
  {"ARS" [14.8 14.6 5.2 57.8 16.4 5.9] "MCI" [14.2 15.3 6.8 63.5 17.8 6.6] "LIV" [13.1 14.1 7.9 60.2 17.1 6.3]
   "CHE" [12.4 13.2 8.1 59.1 15.2 5.4] "NEW" [12.0 12.1 8.4 52.6 14.8 5.1] "AVL" [11.3 10.8 8.2 51.7 12.9 4.5]
   "BHA" [10.9 11.7 9.9 55.4 13.3 4.6] "TOT" [10.6 11.4 9.6 54.3 13.6 4.7] "MUN" [10.4 11.2 10.3 53.0 14.2 4.9]
   "CRY" [10.1 9.6 8.8 44.2 12.1 4.0] "BOU" [9.6 10.4 10.6 47.9 13.0 4.3] "FUL" [9.0 9.4 9.8 50.1 11.8 3.9]
   "BRE" [8.8 9.9 11.0 44.6 11.5 4.1] "EVE" [8.1 8.0 9.4 43.8 10.4 3.5] "LEE" [7.8 8.6 10.9 46.3 11.1 3.6]
   "NFO" [7.6 7.9 10.2 41.5 9.8 3.3] "SUN" [6.9 7.2 11.3 42.0 9.4 3.1] "WHU" [6.4 7.4 12.8 44.9 10.2 3.2]
   "WOL" [5.8 6.6 12.6 45.1 9.9 3.0] "BUR" [5.2 5.9 13.4 39.4 8.6 2.7]})

(def pl-overview
  "League overview tiles as authored in the design; other leagues are derived in xpitch.stats."
  [{:label "xG / match" :value "2.71" :delta "+0.08 vs 25/26" :delta-positive? true
    :caption "Expected goals, both teams combined" :ratio 0.62}
   {:label "Goals / match" :value "2.84" :delta "+0.13 vs 25/26" :delta-positive? true
    :caption "Scoring 4.8% above expected" :ratio 0.66}
   {:label "Shots / match" :value "25.6" :delta "−0.4 vs 25/26" :delta-positive? false
    :caption "8.9 on target per match" :ratio 0.55}
   {:label "Home win rate" :value "44%" :delta "−2 pp vs 25/26" :delta-positive? false
    :caption "Draws 26% · Away wins 30%" :ratio 0.44}])
