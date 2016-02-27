(ns voke.input
  (:require [cljs.core.async :refer [chan <! put!]]
            [goog.events :as events]
            [voke.events :refer [publish-event]]
            [voke.schemas :refer [Entity GameState System]])
  (:import [goog.events KeyCodes])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(def move-key-mappings {KeyCodes.W :up
                        KeyCodes.A :left
                        KeyCodes.S :down
                        KeyCodes.D :right})
(def fire-key-mappings {KeyCodes.DOWN  :down
                        KeyCodes.LEFT  :left
                        KeyCodes.UP    :up
                        KeyCodes.RIGHT :right})
(def key-mappings (merge move-key-mappings fire-key-mappings))

(defn listen-to-keyboard-inputs [event-chan]
  (events/removeAll (.-body js/document))

  (doseq [[goog-event-type event-type-suffix] [[(.-KEYDOWN events/EventType) "down"]
                                               [(.-KEYUP events/EventType) "up"]]]

    (events/listen
      (.-body js/document)
      goog-event-type
      (fn [event]
        (let [code (.-keyCode event)]
          (when (contains? key-mappings code)
            (.preventDefault event)
            (let [event-type (cond
                               (contains? move-key-mappings code) (keyword (str "move-key-" event-type-suffix))
                               (contains? fire-key-mappings code) (keyword (str "fire-key-" event-type-suffix)))]
              (put! event-chan {:type      event-type
                                :direction (key-mappings code)}))))))))

;;; Public

(defn handle-keyboard-events [publish-chan player-id]
  "Listens to keyboard events, and publishes :update-entity events to `publish-chan` for `player-id`'s
  entity id whenever a move key or fire key is pressed/raised."
  (let [event-chan (chan)]
    (listen-to-keyboard-inputs event-chan)

    (go-loop []
      (let [msg (<! event-chan)
            direction (msg :direction)

            update-entity-args (case (msg :type)
                                 :move-key-down [[:intended-move-direction] conj direction]
                                 :move-key-up [[:intended-move-direction] disj direction]
                                 :fire-key-down [[:intended-fire-direction] conj direction]
                                 :fire-key-up [[:intended-fire-direction] disj direction])]

        (publish-event publish-chan {:event-type :update-entity
                                     :origin     :keyboard-input
                                     :entity-id  player-id
                                     :args       update-entity-args})
        (recur)))))