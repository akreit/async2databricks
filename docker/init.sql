-- Create sample data table
CREATE TABLE IF NOT EXISTS sample_data (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    category VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert sample data
INSERT INTO sample_data (name, value, category, created_at) VALUES
    ('Product A', 100.50, 'Electronics', '2024-01-01 10:00:00'),
    ('Product B', 250.75, 'Furniture', '2024-01-02 11:30:00'),
    ('Product C', 75.25, 'Electronics', '2024-01-03 09:15:00'),
    ('Product D', 500.00, 'Appliances', '2024-01-04 14:20:00'),
    ('Product E', 125.99, 'Electronics', '2024-01-05 16:45:00'),
    ('Product F', 350.50, 'Furniture', '2024-01-06 08:30:00'),
    ('Product G', 89.99, 'Electronics', '2024-01-07 12:00:00'),
    ('Product H', 450.00, 'Appliances', '2024-01-08 15:30:00'),
    ('Product I', 199.99, 'Furniture', '2024-01-09 10:45:00'),
    ('Product J', 299.50, 'Electronics', '2024-01-10 13:20:00');

-- Create index for better query performance
CREATE INDEX idx_sample_data_category ON sample_data(category);
CREATE INDEX idx_sample_data_created_at ON sample_data(created_at);
