-- Flyway creates the investment_os schema before applying versioned migrations.
-- Domain tables belong to later stories; this establishes a tracked baseline.
COMMENT ON SCHEMA investment_os IS 'Investment OS application schema, managed by Flyway';
