(ns app.statechart-demo
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [com.fulcrologic.statecharts :as sc]
            [com.fulcrologic.statecharts.chart :refer [statechart]]
            [com.fulcrologic.statecharts.elements :refer [state parallel transition]]
            [com.fulcrologic.statecharts.events :refer [new-event]]
            [com.fulcrologic.statecharts.protocols :as sp]
            [com.fulcrologic.statecharts.util :refer [extend-key]]
            #?(:clj
               [com.fulcrologic.statecharts.simple :as simple])))

;; statechart logic copied from
;; https://github.com/fulcrologic/statecharts/blob/main/src/examples/traffic_light.cljc

(def nk
  "(nk :a \"b\") => :a/b
   (nk :a/b \"c\") => :a.b/c"
  extend-key)

(defn traffic-signal [id initial]
  (let [red     (nk id "red")
        yellow  (nk id "yellow")
        green   (nk id "green")
        initial (nk id (name initial))]
    (state {:id      id
            :initial initial}
           (state {:id red}
                  (transition {:event :swap-flow :target green}))
           (state {:id yellow}
                  (transition {:event :swap-flow :target red}))
           (state {:id green}
                  (transition {:event  :warn-traffic
                               :target yellow})))))

(defn ped-signal [id initial]
  (let [red            (nk id "red")
        flashing-white (nk id "flashing-white")
        white          (nk id "white")
        initial        (nk id (name initial))]
    (state {:id      id
            :initial initial}
           (state {:id red}
                  (transition {:event :swap-flow :target white}))
           (state {:id flashing-white}
                  (transition {:event :swap-flow :target red}))
           (state {:id white}
                  (transition {:event :warn-pedestrians :target flashing-white})))))

(def traffic-lights
  (statechart {}
              (parallel {}
                        (traffic-signal :east-west :green)
                        (traffic-signal :north-south :red)

                        (ped-signal :cross-ew :red)
                        (ped-signal :cross-ns :white))))

(e/defn PedSignal
  [{::sc/keys [configuration]} state-name]
  (e/client
   (dom/div
    (dom/div
     (dom/text (str state-name ": " (e/server (some->> configuration
                                                       (filter #(= (namespace %) state-name))
                                                       first
                                                       name))))))))

(e/defn TrafficSignal
  [{::sc/keys [configuration]} state-name]
  (e/client
   (dom/div
    (dom/div
     (dom/text (str state-name ": " (e/server (some->> configuration
                                                       (filter #(= (namespace %) state-name))
                                                       first
                                                       name))))))))

#?(:clj
   (defn ->env []
     (let [e (simple/simple-env)]
       (simple/register! e ::lights traffic-lights)
       e)))

(e/defn TrafficLights
  []
  (e/server
   (let [env (->env)
         processor (::sc/processor env)
         !state (atom (sp/start! processor env ::lights {::sc/session-id 1}))
         state (e/watch !state)]
     (e/client
      (dom/div (dom/text "Traffic Lights"))
      (dom/div
       (dom/div
        (e/server (TrafficSignal. state "east-west"))
        (e/server (PedSignal. state "cross-ew"))
        (e/server (TrafficSignal. state "north-south"))
        (e/server (PedSignal. state "cross-ns")))
       (dom/div
        (ui/button
         (e/fn []
           (e/server
            (e/snapshot (reset! !state (sp/process-event! processor env state (new-event :warn-pedestrians))))))
         (dom/div (dom/text "warn pedestrians")))
        (ui/button
         (e/fn []
           (e/server
            (e/snapshot (reset! !state (sp/process-event! processor env state (new-event :warn-traffic))))))
         (dom/div (dom/text "warn traffic")))
        (ui/button
         (e/fn []
           (e/server
            (e/snapshot (reset! !state (sp/process-event! processor env state (new-event :swap-flow))))))
         (dom/div (dom/text "swap flow")))))))))
