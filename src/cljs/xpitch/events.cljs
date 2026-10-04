(ns xpitch.events
  (:require [cljs.reader :as reader]
            [re-frame.core :as rf]
            [xpitch.db :as db]))

;; ---------- Effects & coeffects ----------

(def ^:private storage-keys
  {:columns ["xpitch.columns" db/valid-columns?]
   :collapsed ["xpitch.collapsed" db/valid-collapsed?]
   :theme ["xpitch.theme" db/valid-theme?]})

(rf/reg-cofx
 :local-store
 (fn [cofx _]
   (assoc cofx :local-store
          (into {}
                (keep (fn [[k [store-key valid?]]]
                        (try
                          (when-let [raw (.getItem js/localStorage store-key)]
                            (let [v (reader/read-string raw)]
                              (when (valid? v) [k v])))
                          (catch :default _ nil))))
                storage-keys))))

(rf/reg-cofx
 :system-theme
 (fn [cofx _]
   (assoc cofx :system-theme
          (if (and (exists? js/matchMedia) (.-matches (js/matchMedia "(prefers-color-scheme: dark)")))
            "dark"
            "light"))))

(rf/reg-fx
 :persist
 (fn [entries]
   (doseq [[k v] entries]
     (try (.setItem js/localStorage (first (storage-keys k)) (pr-str v))
          (catch :default _ nil)))))

(rf/reg-fx
 :fetch-edn
 (fn [{:keys [url on-success on-failure]}]
   (-> (js/fetch url #js {:headers #js {"Accept" "application/edn"}})
       (.then (fn [res]
                (if (.-ok res)
                  (.text res)
                  (throw (js/Error. (str "HTTP " (.-status res)))))))
       (.then #(rf/dispatch (conj on-success (reader/read-string %))))
       (.catch #(rf/dispatch (conj on-failure (.-message %)))))))

(rf/reg-fx
 :apply-theme
 (fn [theme] (set! (.. js/document -documentElement -dataset -theme) theme)))

(rf/reg-fx
 :scroll-top
 (fn [_] (.scrollTo js/window 0 0)))

(rf/reg-fx
 :focus-handle
 (fn [league-id]
   ;; Wait for the reordered DOM, then keep keyboard focus on the moved league.
   (js/setTimeout
    #(some-> (.querySelector js/document (str "[data-league=\"" (name league-id) "\"] .drag-handle")) .focus)
    0)))

;; ---------- Lifecycle & data ----------

(rf/reg-event-fx
 ::initialize
 [(rf/inject-cofx :local-store) (rf/inject-cofx :system-theme)]
 (fn [{:keys [local-store system-theme]} [_ route]]
   (let [theme (or (:theme local-store) system-theme)]
     {:db (-> db/default-db
              (merge (select-keys local-store [:columns :collapsed]))
              (assoc :theme theme))
      :apply-theme theme
      :fx [[:dispatch [::fetch-leagues]]
           [:dispatch [::route-changed route]]]})))

(rf/reg-event-fx
 ::fetch-leagues
 (fn [{:keys [db]} _]
   {:db (update db :errors dissoc :leagues)
    :fetch-edn {:url "/api/leagues"
                :on-success [::leagues-loaded]
                :on-failure [::load-failed :leagues]}}))

(rf/reg-event-db
 ::leagues-loaded
 (fn [db [_ {:keys [mode provider updated-at data-through leagues]}]]
   (assoc db :leagues leagues :data-mode mode :provider provider
             :updated-at updated-at :data-through data-through)))

(rf/reg-event-db
 ::load-failed
 (fn [db [_ what message]] (assoc-in db [:errors what] message)))

(rf/reg-event-fx
 ::fetch-league
 (fn [{:keys [db]} [_ id]]
   {:db (update db :errors dissoc id)
    :fetch-edn {:url (str "/api/leagues/" (name id))
                :on-success [::league-loaded id]
                :on-failure [::load-failed id]}}))

(rf/reg-event-db
 ::league-loaded
 (fn [db [_ id detail]] (assoc-in db [:details id] detail)))

(rf/reg-event-fx
 ::route-changed
 (fn [{:keys [db]} [_ route]]
   (let [league-id (when (= :league (:name route)) (:id route))]
     (cond-> {:db (assoc db :route route :split :overall :sort db/default-sort)
              :scroll-top true}
       (and league-id (not (get-in db [:details league-id])))
       (assoc :fx [[:dispatch [::fetch-league league-id]]])))))

;; ---------- UI state ----------

(rf/reg-event-db ::set-query (fn [db [_ q]] (assoc db :query q)))

(rf/reg-event-fx
 ::toggle-theme
 (fn [{:keys [db]} _]
   (let [theme (if (= "dark" (:theme db)) "light" "dark")]
     {:db (assoc db :theme theme)
      :apply-theme theme
      :persist {:theme theme}})))

(rf/reg-event-fx
 ::toggle-collapsed
 (fn [{:keys [db]} [_ id]]
   (let [collapsed (let [c (:collapsed db)] (if (c id) (disj c id) (conj c id)))]
     {:db (assoc db :collapsed collapsed)
      :persist {:collapsed collapsed}})))

(rf/reg-event-fx
 ::set-all-collapsed
 (fn [{:keys [db]} [_ collapse?]]
   (let [collapsed (if collapse? (set db/league-ids) #{})]
     {:db (assoc db :collapsed collapsed)
      :persist {:collapsed collapsed}})))

(rf/reg-event-fx
 ::reset-order
 (fn [{:keys [db]} _]
   {:db (assoc db :columns db/default-columns :announcement "League order reset")
    :persist {:columns db/default-columns}}))

(defn move-league
  "Moves league `id` to insertion point {:col :index} (index counted before removal)."
  [columns id {:keys [col index]}]
  (let [from-col (if (some #{id} (columns 0)) 0 1)
        from-index (.indexOf (columns from-col) id)
        removed (update columns from-col #(into (subvec % 0 from-index) (subvec % (inc from-index))))
        index (if (and (= from-col col) (< from-index index)) (dec index) index)
        target (removed col)
        index (max 0 (min index (count target)))]
    (assoc removed col (into (conj (subvec target 0 index) id) (subvec target index)))))

(rf/reg-event-fx
 ::move-league
 (fn [{:keys [db]} [_ id to keep-focus?]]
   (let [columns (move-league (:columns db) id to)
         league-name (some #(when (= id (:id %)) (:name %)) (:leagues db))]
     (cond-> {:db (assoc db
                         :columns columns
                         :announcement (str (or league-name (name id)) " moved to position " (inc (.indexOf (columns (:col to)) id))
                                            " in the " (if (zero? (:col to)) "left" "right") " column"))
              :persist {:columns columns}}
       keep-focus? (assoc :focus-handle id)))))

;; ---------- Drag & drop ----------

(rf/reg-event-db ::arm (fn [db [_ id]] (assoc-in db [:drag :armed] id)))

(rf/reg-event-db ::drag-start (fn [db [_ id]] (assoc-in db [:drag :id] id)))

(rf/reg-event-db
 ::drag-over
 (fn [db [_ col index]]
   (if-let [dragged (get-in db [:drag :id])]
     (let [own (.indexOf (get-in db [:columns col]) dragged)
           ;; Hide the indicator when dropping would leave the order unchanged.
           noop? (and (not= -1 own) (or (= index own) (= index (inc own))))
           drop (when-not noop? {:col col :index index})]
       (if (= drop (get-in db [:drag :drop])) db (assoc-in db [:drag :drop] drop)))
     db)))

(rf/reg-event-fx
 ::drop
 (fn [{:keys [db]} _]
   (let [{:keys [id drop]} (:drag db)]
     (cond-> {:db (assoc db :drag {:armed nil :id nil :drop nil})}
       (and id drop) (assoc :fx [[:dispatch [::move-league id drop false]]])))))

(rf/reg-event-db ::drag-end (fn [db _] (assoc db :drag {:armed nil :id nil :drop nil})))

;; ---------- League view ----------

(rf/reg-event-db ::set-split (fn [db [_ split]] (assoc db :split split)))

(def ^:private asc-first
  "Columns where a smaller number is better sort ascending on first click."
  #{:xga})

(rf/reg-event-db
 ::sort-by
 (fn [db [_ k]]
   (update db :sort (fn [{:keys [key dir]}]
                      (if (= key k)
                        {:key k :dir (if (= dir :desc) :asc :desc)}
                        {:key k :dir (if (asc-first k) :asc :desc)})))))
