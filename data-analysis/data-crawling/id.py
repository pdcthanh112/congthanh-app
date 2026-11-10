import time

class SnowflakeGenerator:
    def __init__(self, worker_id, datacenter_id, sequence=0):
        self.worker_id = worker_id
        self.datacenter_id = datacenter_id
        self.sequence = sequence
        self.epoch = 1640995200000  # Custom epoch (e.g., 2022-01-01T00:00:00Z)
        self.worker_id_bits = 5
        self.datacenter_id_bits = 5
        self.sequence_bits = 12
        self.max_worker_id = -1 ^ (-1 << self.worker_id_bits)
        self.max_datacenter_id = -1 ^ (-1 << self.datacenter_id_bits)
        self.sequence_mask = -1 ^ (-1 << self.sequence_bits)
        self.worker_id_shift = self.sequence_bits
        self.datacenter_id_shift = self.sequence_bits + self.worker_id_bits
        self.timestamp_shift = self.sequence_bits + self.worker_id_bits + self.datacenter_id_bits
        self.last_timestamp = -1

    def _current_timestamp(self):
        return int(time.time() * 1000)

    def _wait_for_next_millis(self, last_timestamp):
        timestamp = self._current_timestamp()
        while timestamp <= last_timestamp:
            timestamp = self._current_timestamp()
        return timestamp

    def generate(self):
        timestamp = self._current_timestamp()
        if timestamp < self.last_timestamp:
            raise Exception("Clock moved backwards. Refusing to generate id")

        if timestamp == self.last_timestamp:
            self.sequence = (self.sequence + 1) & self.sequence_mask
            if self.sequence == 0:
                timestamp = self._wait_for_next_millis(self.last_timestamp)
        else:
            self.sequence = 0

        self.last_timestamp = timestamp

        snowflake_id = (
            ((timestamp - self.epoch) << self.timestamp_shift)
            | (self.datacenter_id << self.datacenter_id_shift)
            | (self.worker_id << self.worker_id_shift)
            | self.sequence
        )
        return snowflake_id


# Initialize generator
generator = SnowflakeGenerator(worker_id=1, datacenter_id=1)

# Generate 20 Snowflake IDs
ids = [generator.generate() for _ in range(20)]
print(ids)
