(ns firedraft.common.page)

(defn nav!
  [session page]
  (swap! session assoc :page page))
