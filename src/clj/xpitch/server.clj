(ns xpitch.server
  (:require [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [ring.adapter.jetty :as jetty]
            [xpitch.api-football :as api]
            [xpitch.data :as data]
            [xpitch.source :as source]
            [xpitch.sync :as sync])
  (:gen-class))

(defn- ok [body] {:status 200 :body body})

(defn- leagues-handler [_]
  (ok (source/leagues-payload)))

(defn- league-handler [{{:keys [id]} :path-params}]
  (let [league-id (keyword id)]
    (if (contains? data/leagues league-id)
      (ok (source/league-payload league-id))
      {:status 404 :body {:error (str "Unknown league: " id)}})))

(defn- status-handler [_]
  (let [{:keys [config last-sync errors leagues]} @sync/state]
    (ok {:live-enabled (sync/enabled?)
         :provider (:provider config)
         :season (:season config)
         :refresh-minutes (:refresh-minutes config)
         :last-sync (some-> last-sync str)
         :requests @api/limits
         :errors errors
         :cached (into {} (for [[id d] leagues]
                            [id {:teams (count (:standings d))
                                 :finished-matches (count (:fixtures d))
                                 :matches-with-stats (count (:fixture-stats d))}]))})))

(def app
  (ring/ring-handler
   (ring/router
    ["/api"
     ["/leagues" {:get leagues-handler}]
     ["/leagues/:id" {:get league-handler}]
     ["/status" {:get status-handler}]]
    {:data {:muuntaja m/instance
            :middleware [muuntaja/format-middleware]}})
   (ring/routes
    (ring/create-resource-handler {:path "/" :root "public"})
    (ring/create-default-handler))))

(defn start!
  "Starts the API-Football sync (if configured) and Jetty. Port defaults to $PORT or 3000."
  ([] (start! {}))
  ([{:keys [port join?] :or {join? false}}]
   (sync/start!)
   (let [port (or port (some-> (System/getenv "PORT") parse-long) 3000)]
     (println (str "xPitch running at http://localhost:" port "/"))
     (jetty/run-jetty #'app {:port port :join? join?}))))

(defn -main [& _]
  (start! {:join? true}))
