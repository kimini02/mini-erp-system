-- Unknown historical retirement dates remain NULL; never infer them from audit timestamps.
ALTER TABLE users ADD retired_date DATE NULL;
