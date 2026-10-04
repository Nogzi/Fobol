(ns xpitch.teams
  "Display names and crest codes for team names as football-data.co.uk spells them.
  Unknown teams fall back to the CSV name and a derived code.")

(def ^:private known
  ;; csv-name [code full-name short-name?]
  {;; Premier League
   "Arsenal" ["ARS" "Arsenal"] "Aston Villa" ["AVL" "Aston Villa"] "Bournemouth" ["BOU" "Bournemouth"]
   "Brentford" ["BRE" "Brentford"] "Brighton" ["BHA" "Brighton"] "Chelsea" ["CHE" "Chelsea"]
   "Coventry" ["COV" "Coventry City" "Coventry"] "Crystal Palace" ["CRY" "Crystal Palace"] "Everton" ["EVE" "Everton"]
   "Fulham" ["FUL" "Fulham"] "Hull" ["HUL" "Hull City" "Hull"] "Ipswich" ["IPS" "Ipswich Town" "Ipswich"]
   "Leeds" ["LEE" "Leeds United" "Leeds"] "Liverpool" ["LIV" "Liverpool"] "Man City" ["MCI" "Manchester City" "Man City"]
   "Man United" ["MUN" "Manchester United" "Man United"] "Newcastle" ["NEW" "Newcastle"]
   "Nott'm Forest" ["NFO" "Nottingham Forest" "Nott'm Forest"] "Sunderland" ["SUN" "Sunderland"]
   "Tottenham" ["TOT" "Tottenham"] "West Ham" ["WHU" "West Ham"] "Wolves" ["WOL" "Wolves"] "Burnley" ["BUR" "Burnley"]
   ;; La Liga
   "Alaves" ["ALA" "Alavés"] "Ath Bilbao" ["ATH" "Athletic Club"] "Ath Madrid" ["ATM" "Atlético Madrid" "Atlético"]
   "Barcelona" ["BAR" "Barcelona"] "Betis" ["BET" "Real Betis"] "Celta" ["CEL" "Celta Vigo"] "Elche" ["ELC" "Elche"]
   "Espanol" ["ESP" "Espanyol"] "Getafe" ["GET" "Getafe"] "La Coruna" ["DEP" "Deportivo La Coruña" "Deportivo"]
   "Levante" ["LEV" "Levante"] "Malaga" ["MAL" "Málaga"] "Osasuna" ["OSA" "Osasuna"] "Real Madrid" ["RMA" "Real Madrid"]
   "Santander" ["RAC" "Racing Santander" "Racing"] "Sevilla" ["SEV" "Sevilla"] "Sociedad" ["RSO" "Real Sociedad"]
   "Valencia" ["VAL" "Valencia"] "Vallecano" ["RAY" "Rayo Vallecano" "Rayo"] "Villarreal" ["VIL" "Villarreal"]
   "Mallorca" ["MLL" "Mallorca"] "Girona" ["GIR" "Girona"] "Oviedo" ["OVI" "Real Oviedo"]
   ;; Bundesliga
   "Augsburg" ["FCA" "FC Augsburg" "Augsburg"] "Bayern Munich" ["FCB" "Bayern Munich"] "Dortmund" ["BVB" "Borussia Dortmund" "Dortmund"]
   "Ein Frankfurt" ["SGE" "Eintracht Frankfurt" "Frankfurt"] "Elversberg" ["SVE" "SV Elversberg" "Elversberg"]
   "FC Koln" ["KOE" "1. FC Köln" "Köln"] "Freiburg" ["SCF" "SC Freiburg" "Freiburg"] "Hamburg" ["HSV" "Hamburger SV"]
   "Hoffenheim" ["TSG" "Hoffenheim"] "Leverkusen" ["B04" "Bayer Leverkusen" "Leverkusen"]
   "M'gladbach" ["BMG" "Borussia Mönchengladbach" "Gladbach"] "Mainz" ["M05" "Mainz 05"] "Paderborn" ["SCP" "SC Paderborn" "Paderborn"]
   "RB Leipzig" ["RBL" "RB Leipzig"] "Schalke 04" ["S04" "Schalke 04"] "Stuttgart" ["VFB" "VfB Stuttgart" "Stuttgart"]
   "Union Berlin" ["FCU" "Union Berlin"] "Werder Bremen" ["SVW" "Werder Bremen"] "Wolfsburg" ["WOB" "VfL Wolfsburg" "Wolfsburg"]
   "St Pauli" ["STP" "St. Pauli"] "Heidenheim" ["HDH" "Heidenheim"]
   ;; Serie A
   "Atalanta" ["ATA" "Atalanta"] "Bologna" ["BOL" "Bologna"] "Cagliari" ["CAG" "Cagliari"] "Como" ["COM" "Como"]
   "Fiorentina" ["FIO" "Fiorentina"] "Frosinone" ["FRO" "Frosinone"] "Genoa" ["GEN" "Genoa"] "Inter" ["INT" "Inter"]
   "Juventus" ["JUV" "Juventus"] "Lazio" ["LAZ" "Lazio"] "Lecce" ["LEC" "Lecce"] "Milan" ["MIL" "AC Milan"]
   "Monza" ["MON" "Monza"] "Napoli" ["NAP" "Napoli"] "Parma" ["PAR" "Parma"] "Roma" ["ROM" "Roma"]
   "Sassuolo" ["SAS" "Sassuolo"] "Torino" ["TOR" "Torino"] "Udinese" ["UDI" "Udinese"] "Venezia" ["VEN" "Venezia"]
   "Verona" ["VER" "Hellas Verona" "Verona"] "Cremonese" ["CRE" "Cremonese"] "Pisa" ["PIS" "Pisa"]
   ;; Ligue 1
   "Angers" ["ANG" "Angers"] "Auxerre" ["AJA" "Auxerre"] "Brest" ["B29" "Brest"] "Le Havre" ["HAC" "Le Havre"]
   "Le Mans" ["LMS" "Le Mans"] "Lens" ["RCL" "Lens"] "Lille" ["LOS" "Lille"] "Lorient" ["LOR" "Lorient"]
   "Lyon" ["OL" "Lyon"] "Marseille" ["OM" "Marseille"] "Monaco" ["ASM" "Monaco"] "Nice" ["NIC" "Nice"]
   "Paris FC" ["PFC" "Paris FC"] "Paris SG" ["PSG" "Paris Saint-Germain" "Paris SG"] "Rennes" ["REN" "Rennes"]
   "Strasbourg" ["RCS" "Strasbourg"] "Toulouse" ["TFC" "Toulouse"] "Troyes" ["ETR" "Troyes"]
   "Nantes" ["NAN" "Nantes"] "Metz" ["FCM" "Metz"]
   ;; 3F Superliga
   "Aarhus" ["AGF" "AGF"] "Brondby" ["BIF" "Brøndby IF" "Brøndby"] "FC Copenhagen" ["FCK" "FC København" "København"]
   "Horsens" ["ACH" "AC Horsens" "Horsens"] "Lyngby" ["LBK" "Lyngby BK" "Lyngby"] "Midtjylland" ["FCM" "FC Midtjylland" "Midtjylland"]
   "Nordsjaelland" ["FCN" "FC Nordsjælland" "Nordsjælland"] "Odense" ["OB" "OB"] "Randers FC" ["RFC" "Randers FC" "Randers"]
   "Silkeborg" ["SIF" "Silkeborg IF" "Silkeborg"] "Sonderjyske" ["SJE" "SønderjyskE"] "Viborg" ["VFF" "Viborg FF" "Viborg"]
   "Vejle" ["VB" "Vejle Boldklub" "Vejle"] "Fredericia" ["FCF" "FC Fredericia" "Fredericia"]})

(defn lookup
  "{:code :name :short} for a CSV team name; :code is nil when unknown."
  [csv-name]
  (if-let [[code full short] (known csv-name)]
    {:code code :name full :short (or short full)}
    {:code nil :name csv-name :short csv-name}))
