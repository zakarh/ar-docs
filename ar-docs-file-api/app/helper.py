from string import digits
from uuid import uuid4
import time

def extract_num(s: str) -> int:
    n = 0
    for c in s:
        if c in digits:
            n *= 10
            n += int(c)
    return n


def gen_id(r=8):
    return "".join([str(uuid4()).replace("-", "") for i in range(r)])

def current_nanosec_time():
    return time.time_ns()