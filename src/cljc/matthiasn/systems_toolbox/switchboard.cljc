(ns matthiasn.systems-toolbox.switchboard
  (:require [matthiasn.systems-toolbox.component :as comp]
            [matthiasn.systems-toolbox.switchboard.route :as rt]
            [matthiasn.systems-toolbox.switchboard.observe :as obs]
            [matthiasn.systems-toolbox.switchboard.init :as i]
    #?(:clj
            [clojure.core.async :refer [put! chan pipe sub tap]]
       :cljs [cljs.core.async :refer [put! chan pipe sub tap]])
    #?(:clj
            [clojure.pprint :as pp]
       :cljs [cljs.pprint :as pp])
    #?(:clj
            [clojure.tools.logging :as l]
       :cljs [matthiasn.systems-toolbox.log :as l])
    #?(:clj
            [io.aviso.exception :as ex])
    #?(:clj
            [clojure.spec.alpha :as s]
       :cljs [cljs.spec.alpha :as s])))

(defn- self-register
  "Registers switchboard itself as another component that can be wired. Useful
   for communication with the outside world / within hierarchies where a
   subsystem has its own switchboard."
  [{:keys [cmp-state msg-payload cmp-id]}]
  (swap! cmp-state assoc-in [:components cmp-id] msg-payload)
  (swap! cmp-state assoc-in [:switchboard-id] cmp-id)
  {})

(defn mk-state
  "Create initial state atom for switchboard component."
  [_put-fn]
  {:state (atom {:components {}
                 :subs       #{}
                 :taps       #{}
                 :fh-taps    #{}})})

(defn attach-to-firehose
  "Attaches a component to firehose channel. For example for observational
   components."
  [{:keys [current-state msg-payload cmp-id]}]
  (let [to msg-payload
        sw-firehose-mult (:firehose-mult (cmp-id (:components current-state)))
        to-comp (to (:components current-state))]
    (try
      (do
        (tap sw-firehose-mult (:in-chan to-comp))
        {:new-state (update-in current-state [:fh-taps] conj {:from cmp-id
                                                              :to   to
                                                              :type :fh-tap})})
      #?(:clj  (catch Exception e
                 (l/error "Could not create tap: " cmp-id " -> " to " - "
                          (ex/format-exception e)))
         :cljs (catch js/Object e (l/error "Could not create tap: " cmp-id
                                           " -> " to " - " e))))))

(defn send-to
  "Send message to specified component."
  [{:keys [cmp-state msg-payload]}]
  (let [{:keys [to msg]} msg-payload
        dest-comp (to (:components @cmp-state))]
    (put! (:in-chan dest-comp) msg))
  {})

(defn wire-all-out-channels
  "Function for calling the system-ready-fn on each component, which will pipe
   the channel used by the put-fn to the out-chan when the system is connected.
   Otherwise, messages sent before all channels are wired would get lost."
  [{:keys [cmp-state]}]
  (doseq [[_ cmp] (:components @cmp-state)]
    ((:system-ready-fn cmp))))

(def handler-map
  {:cmd/route              rt/route-handler
   :cmd/route-all          rt/route-all-handler
   :cmd/wire-comp          (i/wire-or-init-comp false)
   :cmd/init-comp          (i/wire-or-init-comp true)
   :cmd/shutdown-all       i/shutdown-all
   :cmd/shutdown           i/shutdown-cmp
   :cmd/attach-to-firehose attach-to-firehose
   :cmd/self-register      self-register
   :cmd/observe-state      obs/observe-state
   :cmd/send               send-to
   :status/system-ready    wire-all-out-channels})

(defn xform-fn
  "Transformer function for switchboard state snapshot. Allows serialization of
   snaphot for sending over WebSockets."
  [m]
  (update-in m [:components] (fn [cmps]
                               (into {} (mapv (fn [[k v]] [k k]) cmps)))))

(defn component
  "Creates a switchboard component that wires individual components together
   into a communicating system."
  ([switchboard-id]
   (component switchboard-id {}))
  ([switchboard-id cmp-opts]
   (let [switchboard (comp/make-component
                       {:cmp-id            switchboard-id
                        :state-fn          mk-state
                        :handler-map       handler-map
                        :state-spec        :st.switchboard/state-spec
                        :opts              (merge {:msgs-on-firehose      false
                                                   :snapshots-on-firehose true}
                                                  cmp-opts)
                        :snapshot-xform-fn xform-fn})
         sw-in-chan (:in-chan switchboard)]
     (put! sw-in-chan [:cmd/self-register switchboard])
     switchboard)))

(defn send-cmd
  "Send message to the specified switchboard component."
  [switchboard cmd]
  (put! (:in-chan switchboard) cmd))

(defn send-mult-cmd
  "Send messages to the specified switchboard component."
  [switchboard cmds]
  (doseq [cmd cmds] (when cmd (put! (:in-chan switchboard) cmd)))
  (put! (:in-chan switchboard) [:status/system-ready]))
