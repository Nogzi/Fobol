(ns xpitch.db)

(def league-ids [:premier-league :la-liga :bundesliga :serie-a :ligue-1 :superliga])

(def default-columns
  [[:premier-league :bundesliga :ligue-1]
   [:la-liga :serie-a :superliga]])

(def default-sort {:key :x-pts :dir :desc})

(def default-db
  {:route {:name :home}
   :query ""
   :theme "light"
   :columns default-columns
   :collapsed #{}
   :leagues nil
   :details {}
   :errors {}
   :split :overall
   :sort default-sort
   :drag {:armed nil :id nil :drop nil}
   :announcement ""})

(defn valid-columns? [v]
  (and (vector? v)
       (= 2 (count v))
       (every? vector? v)
       (= (sort (apply concat v)) (sort league-ids))))

(defn valid-collapsed? [v]
  (and (set? v) (every? (set league-ids) v)))

(defn valid-theme? [v] (contains? #{"light" "dark"} v))

(defn parse-hash
  "#/league/<id> → league route, anything else → home."
  [hash]
  (if-let [[_ id] (re-find #"^#/league/([\w-]+)" (or hash ""))]
    (let [k (keyword id)]
      (if (some #{k} league-ids) {:name :league :id k} {:name :home}))
    {:name :home}))
