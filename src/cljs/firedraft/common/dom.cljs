(ns firedraft.common.dom
  (:require [clojure.string :as str]))

(defn elem [id] (.getElementById js/document id))

(defn value
  [x]
  (-> x .-value))

(defn target-value
  [x]
  (-> x .-target .-value))

(defn elem-val [id] (-> (.getElementById js/document id)
                        (value)))

(def fire-emoji "🔥")

(defn header []
  [:div.content
   [:h1.title  (str fire-emoji " Firedraft")]])

(defn classes [& strs]
  (str/join " " strs))
