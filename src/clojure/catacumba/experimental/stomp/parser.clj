(ns catacumba.experimental.stomp.parser
  (:import (java.io PushbackReader StringReader)))

;; this character denotes the end of a frame.
(def *frame-end* 0) ;; ASCII null

;; this character denotes the end of a line
(def *line-end* 10) ;; ASCII linefeed

;; delimeter between attributes and values
(def *header-delimiter* 58) ;; ASCII colon

(defrecord StompFrame [command headers body])

(defn byte-length
  "Returns the length in bytes of the provided sequence. The sequence
  needs to be implement CharSequence (i.e. a String, CharBuffer, etc.)"
  [body]
  (.codePointCount body 0 (count body)))

(defn emit
  "Prints the provided frame to *out*."
  [frame]
  (with-out-str
    ;; the command
    (print (str (.toUpperCase (name (:command frame))) (char *line-end*)))

    ;; the headers
    (doseq [[key val] (:headers frame)]
      (print (str (name key) ":" (.trim val) (char *line-end*))))

    ;; calculate the length of the body for :send
    (print (str "content-length:"
                (byte-length (:body frame)) (char *line-end*)))

    ;; the body
    (print (char *line-end*))
    (print (apply str (:body frame)))
    (print (char *frame-end*))))

(defmulti get-reader
  "Returns a PushBackReader for the provided Object. We want to wrap
  another Reader but we'll cast to a String and read that if required."
  :class)

(defmethod get-reader Readable
  [frame-seq]
  (PushbackReader. frame-seq))

(defmethod get-reader :default
  [frame-seq]
  (PushbackReader. (StringReader. (apply str frame-seq))))

(defn peek-for
  "Returns the next character from the Reader, checks it against the
  expected value, then pushes it back on the reader."
  [peek-int  reader]
  (let [int-in (.read reader)]
    (.unread reader int-in)
    (if (= int-in peek-int) true false)))

(defn read-command
  "Reads in the command on the STOMP frame."
  [reader]
  (loop [int-in (.read reader) buffer []]
    (cond

      (= int-in -1)
      (throw (Exception. "End of file reached while reading command"))

      (= int-in *frame-end*)
      (throw (Exception. "End of frame reached while reading command"))

      (= int-in *line-end*)
      (apply str buffer)

      :else
      (recur (.read reader) (conj buffer (char int-in))))))

(defn read-header-key
  "Reads in the key name for a STOMP header."
  [reader]
  (loop [int-in (.read reader) buffer []]
    (cond

      (= int-in -1)
      (throw (Exception. "End of file reached while reading header key"))

      (= int-in *frame-end*)
      (throw (Exception. "End of frame reached while reading header key"))

      (= int-in *header-delimiter*)
      (apply str buffer)

      :else
      (recur (.read reader) (conj buffer (char int-in))))))

(defn read-header-value
  "Reads in the value for a STOMP header."
  [reader]
  (loop [int-in (.read reader) buffer []]
    (cond

      (= int-in -1)
      (throw (Exception. "End of file reached while reading header value"))

      (= int-in *frame-end*)
      (throw (Exception. "End of frame reached while reading header value"))

      (= int-in *line-end*)
      (apply str buffer)

      :else
      (recur (.read reader) (conj buffer (char int-in))))))

(defn read-body
  "Lazily reads in the body of a STOMP frame."
  [reader]
  (let [sb (StringBuffer.)]
    (loop [int-in (.read reader)]
      (cond
        ;; the frame end marker should be the last bit of data
        (and (= int-in *frame-end*) (not (peek-for -1 reader)))
        (throw (Exception. "End of frame reached while reading body"))

        (= int-in -1)
        (.toString sb)

        (not= int-in *frame-end*)
        (do
          (.append sb (char int-in))
          (recur (.read reader)))))))

(defn parse-frame
  "Parses the STOMP frame data from the provided reader into a
  hash-map (frame-struct)."
  [reader]
  (loop [int-in (.read reader) parsing :command frame {}]
    (cond

      (= int-in -1)
      (throw (Exception. "End of file reached without an end of frame"))

      (= parsing :command)
      (do (.unread reader int-in)
          (recur nil :headers  (assoc frame :command (read-command reader))))

      (= parsing :headers)
      (recur nil
             (if (peek-for *line-end* reader) :body :headers)
             (if (peek-for *line-end* reader) frame
                 (assoc frame :headers (assoc (:headers frame)
                                              (keyword (.toLowerCase (read-header-key reader)))
                                              (read-header-value reader)))))

      (= parsing :body)
      (do (.read reader)
          (recur nil :complete
                 (assoc frame :body (read-body reader))))

      (= parsing :complete)
      (map->StompFrame frame)

      (not= int-in *frame-end*)
      (recur (.read reader) parsing frame))))

(defn parse
  "Parses the provided STOMP frame data into a
  hash-map (frame-struct). It will read the headers greedily but
  return the body of the frame lazily; the body will be a sequence."
  [frame-data]
  (parse-frame (get-reader frame-data)))
