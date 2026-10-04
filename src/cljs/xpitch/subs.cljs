(ns xpitch.subs
  (:require [re-frame.core :as rf]
            [xpitch.util :as util]))

(doseq [k [:route :query :theme :columns :collapsed :split :sort :drag :announcement :errors :data-mode :updated-at :provider :data-through]]
  (rf/reg-sub k (fn [db _] (get db k))))

(rf/reg-sub
 ::leagues-by-id
 (fn [db _] (into {} (map (juxt :id identity)) (:leagues db))))

(rf/reg-sub ::leagues (fn [db _] (:leagues db)))

(rf/reg-sub
 ::league
 :<- [::leagues-by-id]
 (fn [by-id [_ id]] (by-id id)))

(rf/reg-sub
 ::filtered-standings
 (fn [[_ id]] [(rf/subscribe [::league id]) (rf/subscribe [:query])])
 (fn [[league query] _]
   (filterv #(util/matches-query? (:name %) query) (:standings league))))

(rf/reg-sub
 ::detail
 (fn [db [_ id]] (get-in db [:details id])))

(rf/reg-sub
 ::advanced-rows
 (fn [[_ id]] [(rf/subscribe [::detail id]) (rf/subscribe [:split]) (rf/subscribe [:sort]) (rf/subscribe [:query])])
 (fn [[detail split {:keys [key dir]} query] _]
   (let [cmp (if (= dir :asc) compare #(compare %2 %1))]
     (->> (get-in detail [:advanced split])
          (filter #(util/matches-query? (:name %) query))
          ;; Missing values sort last in either direction; ties keep league-position order.
          (sort (fn [a b]
                  (let [va (get a key) vb (get b key)
                        c (cond (and (nil? va) (nil? vb)) 0
                                (nil? va) 1
                                (nil? vb) -1
                                :else (cmp va vb))]
                    (if (zero? c) (compare (:pos a) (:pos b)) c))))
          vec))))

(rf/reg-sub
 ::notes
 :<- [::leagues]
 (fn [leagues _] (keep (fn [{:keys [name note]}] (when note (str name ": " note))) leagues)))
