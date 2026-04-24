import multiprocessing

bind = "127.0.0.1:8000"
workers = multiprocessing.cpu_count() * 2 + 1
worker_class = "sync"
timeout = 0  # no timeout
max_requests = 1000
max_requests_jitter = 50
accesslog = "/home/maestro/maestro/server/logs/gunicorn-access.log"
errorlog = "/home/maestro/maestro/server/logs/gunicorn-error.log"
loglevel = "info"
