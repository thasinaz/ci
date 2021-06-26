(define (new-pastry type val)
  (list type val))

(define (pastry-type pastry)
  (car pastry))

(define (pastry-val pastry)
  (cadr pastry))

(define (pastry-op pastry op)
  (define pastry-op
    (list (cons 'cook cook-pastry)
          (cons 'eat eat-pastry)
          (cons 'decorate decorate-pastry)))

  (let ((rc (find (lambda (x) (eq? op (car x))) pastry-op)))
    (if rc
        ((cdr rc) pastry)
        (error "non implement"))))

(define (new-beignet pastry)
  (new-pastry 'beignet pastry))

(define (new-cruller pastry)
  (new-pastry 'cruller pastry))

(define (cook-pastry pastry)
  (let ((type (pastry-type pastry)))
    (cond ((eq? type 'beignet) cook-beignet)
          ((eq? type 'cruller) cook-cruller))))

(define (eat-pastry pastry)
  (let ((type (pastry-type pastry)))
    (cond ((eq? type 'beignet) eat-beignet)
          ((eq? type 'cruller) eat-cruller))))

(define (decorate-pastry pastry)
  (let ((type (pastry-type)))
    (cond ((eq? type 'beignet) decorate-beignet)
          ((eq? type 'cruller) decorate-cruller))))

(define (cook-beignet beignet)
  'cook-beignet)

(define (eat-beignet beignet)
  'eat-beignet)

(define (decorate-beignet beignet)
  'decorate-beignet)

(define (cook-cruller cruller)
  'cook-cruller)

(define (eat-cruller cruller)
  'eat-cruller)

(define (decorate-cruller cruller)
  'decorate-cruller)
