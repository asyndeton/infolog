(ns infolog.components.viz-nesting
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame]
            [reagent.core :as r]
            [cljsjs.d3]
            [taoensso.encore :as enc  :refer (logf log logp)]))

(def diameter 960)
(def margin 10)
(def inner-diameter (- diameter margin margin))

(def x-scale (.. js/d3 -scale (linear) (range #js [0 inner-diameter])))
(def y-scale (.. js/d3 -scale (linear) (range #js [0 inner-diameter])))

(def color-scale (.. js/d3 -scale
                     (linear)
                     (domain #js [-1 5])
                     (range (clj->js ["hsl(185,60%,99%)" "hsl(187,40%,70%)"]))
                     (interpolate (aget js/d3 "interpolateHcl"))))

(def pack (.. js/d3 -layout
              (pack)
              (padding 2)
              (size #js [inner-diameter inner-diameter])
              (value (fn [d] (aget d "size")))))

(def focus (r/atom "root"))

(defn translate
  ([a] (translate a a))
  ([a b]
   (str "translate(" a "," b ")")))


(declare mk-inner-node)

(defn create-structure [top content]
  (let [leafs (filter #(empty? (:path %)) content)
        inner-nodes (remove #(empty? (:path %)) content)
        ign (group-by (fn [n] (first (:path n))) inner-nodes)]
    {:name top :inner-node true :root (= top "root") :children (into leafs (map mk-inner-node ign))}))


(defn mk-inner-node [[top nodes]]
  (let [nodes (map (fn [e] (assoc e :path (rest (:path e)))) nodes)]
    (create-structure top nodes)))

(defn make-module-db [modules]
  (into {} (map (fn [{:keys [name] :as e}] [name e]) modules)))

(defn join-data [max-depth mods data]
  (map (fn [[_ clauses]]
         (let [size (reduce + (map :calls-in-body clauses))
               depth (apply max (map :depth clauses))
               weight (double (/ depth max-depth))
               module-name (:module (first clauses))
               module-path (conj (vec (:path (mods module-name))) module-name)]
           {:module module-name
            :path module-path
            :name (:pa (first clauses))
            :size size :weight weight :children []}))
       (group-by :mpa data)))

(defn mk-data [modules data]
  (let [mods (make-module-db @modules)
        max-depth (apply max (map :depth @data))
        joined (join-data max-depth mods @data)
        nodes (create-structure "root" joined)]
    (reaction nodes)))


(defn update-fn [d]
  (logp :update-viz)
  (let [dse js/d3
        g (.select dse ".viz-g")
        t (.select dse ".viz-t")
        nodes (.nodes pack (clj->js d))
        points (.. g
                   (selectAll "circle")
                   (data nodes))
        texts (.. t
                  (selectAll "text")
                  (data nodes))]
    (.. points
        (enter)
        (append "circle")
        (attr "class" (fn [d] (str "nesting-node" (when (aget d "inner-node") " nesting-inner-node"))))
        (attr "r" (fn [d] (aget d "r")))
        (attr "fill" (fn [d]
                       (let [w (aget d "weight")
                             children? (aget d "children")
                             depth (aget d "depth")]
                         (cond (pos? w) "darkred"
                               children? (color-scale depth)
                               :otherwise "WhiteSmoke"))))
        (attr "fill-opacity" (fn [d] (aget d "weight")))
        (attr "transform" (fn [d] (translate (aget d "x") (aget d "y"))))
        (on "click" (fn [d] (log d (if (aget d "inner-node") 1 2))
                      (if (= d @focus)
                        (reset! focus "root")
                        
                             (if (aget d "inner-node")
                               (do (log "focus" d) (reset! focus d))
                               (let [d (aget d "parent")] (log "focus" d) (reset! focus d)))))))
    (.. texts
        (enter)
        (append "text")
        (attr "class" "nesting-label")
        (attr "transform" (fn [d] (translate (aget d "x") (aget d "y"))))
        (text (fn [d]  (aget d "name"))))
    (.. texts
        (style "display" "none"
               #_(fn [d] (let [p (aget d "parent")]
                        (if (and p (= @focus p)) nil "none")))))))

(defn mount-fn [rc data]
  (logp :mounting-viz)
  (let [el (.getDOMNode rc)
        svg (.. js/d3
                (select el)
                (append "svg")
                (attr "width" diameter)
                (attr "height" diameter))]
    (.. svg
        (append "g")
        (attr "class" "viz-g")
        (attr "transform" (translate margin)))

    (.. svg
        (append "g")
        (attr "class" "viz-t"))

    (update-fn data)))

(defn nesting-viz []
  (let [modules (re-frame/subscribe [:raw-modules])
        data (mk-data modules (re-frame/subscribe [:nesting]))]
    (r/create-class
     {:component-did-mount (fn [rc] (mount-fn rc @data))
      :component-did-update (fn [_] (update-fn @data))
      :reagent-render (fn [_]
                        [:div
                         [:div.nesting-viz {:data-focus @focus :data-count (count @data)}]])})))
