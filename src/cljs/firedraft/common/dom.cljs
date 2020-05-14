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

(def header
  [:div.section {:id "app-title"}
   [:div.container
    [:div.content
     [:h1.title
      "Draftwith.me"]]]])

(def footer
  [:footer.footer
   [:div.content.has-text-centered
    [:p "author: " [:span.is-family-code "@ccann"]]
    [:p "Draftwith.me is unofficial Fan Content permitted under the Fan Content Policy. Not approved/endorsed by Wizards. Portions of the materials used are property of Wizards of the Coast. ©Wizards of the Coast LLC."]]])

(defn classes [& strs]
  (str/join " " strs))

(defn copy-to-clipboard
  [text]
  (let [e (js/document.createElement "textarea")]
    (set! (.-value e) text)
    (js/document.body.appendChild e)
    (.select e)
    (js/document.execCommand "copy")
    (js/document.body.removeChild e)))
