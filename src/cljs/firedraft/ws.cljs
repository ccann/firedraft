(ns firedraft.ws
  (:require-macros
   [cljs.core.async.macros :refer (go go-loop)]
   [re-frame.core :refer [dispatch reg-fx]])
  (:require
   [cljs.core.async :refer (<! >! put! chan)]
   [taoensso.encore :as encore :refer [have]]
   [taoensso.sente :as sente :refer (cb-success?)]))

(let [{:keys [chsk ch-recv send-fn state]}
      ;; assigns a client ID to this browser tab
      (sente/make-channel-socket! "/ws"
                                  ;; ?csrf-token
                                  {:type :auto})]
  (def chsk chsk)
  ;; receive channel
  (def ch-chsk ch-recv)
  ;;send function
  (def send! send-fn)
  ;; Watchable, read-only atom
  (def chsk-state state))

(defmulti handle-event :id)

(defmethod handle-event :chsk/state
  [{:keys [?data]}]
  (.log js/console (str "state changed: " ?data)))

(defmethod handle-event :chsk/handshake
  [{:keys [?data]}]
  (.log js/console (str "connection established: " ?data)))

(defmethod handle-event :default
  [ev-msg]
  (.log js/console (str "Unhandled event: " (:event ev-msg))))

(defn event-handler [msg] (handle-event msg))

(def router (atom nil))

(defn stop-router! []
  (when-let [stop-f @router] (stop-f)))

(defn start-router! []
  (stop-router!)
  (reset! router (sente/start-chsk-router! ch-chsk event-handler)))
