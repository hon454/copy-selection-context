TIMEOUT_SECONDS = 10
RETRY_COUNT = 3


def request_policy():
    return {"timeout": TIMEOUT_SECONDS, "retries": RETRY_COUNT}
