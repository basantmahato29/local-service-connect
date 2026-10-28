CREATE DATABASE IF NOT EXISTS local_service_connect;
USE local_service_connect;

CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  phone VARCHAR(15),
  password VARCHAR(255) NOT NULL,            -- BCrypt hash
  role ENUM('CUSTOMER','PROVIDER','ADMIN') NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,     -- admin can disable accounts
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE customers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  address VARCHAR(255),
  area VARCHAR(100),
  city VARCHAR(100),
  pincode VARCHAR(10),
  profile_image VARCHAR(255),
  formatted_address VARCHAR(255),
  latitude DECIMAL(10,7),
  longitude DECIMAL(10,7),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE providers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  business_name VARCHAR(150) NOT NULL,
  description TEXT,
  profile_image VARCHAR(255),
  experience_years INT DEFAULT 0,
  approx_price DECIMAL(10,2),
  verification_status ENUM('PENDING','VERIFIED','REJECTED') NOT NULL DEFAULT 'PENDING',
  availability BOOLEAN NOT NULL DEFAULT FALSE,
  rating DECIMAL(2,1) DEFAULT 0.0,
  review_count INT DEFAULT 0,
  formatted_address VARCHAR(255),
  latitude DECIMAL(10,7),
  longitude DECIMAL(10,7),
  service_radius_km DECIMAL(5,2) DEFAULT 10.0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE services (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE,
  description VARCHAR(255),
  icon VARCHAR(100),
  status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE provider_services (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider_id BIGINT NOT NULL,
  service_id BIGINT NOT NULL,
  UNIQUE (provider_id, service_id),
  FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE,
  FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE
);

CREATE TABLE provider_areas (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider_id BIGINT NOT NULL,
  area VARCHAR(100) NOT NULL,
  city VARCHAR(100) NOT NULL,
  INDEX idx_area (area, city),
  FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE
);

CREATE TABLE bookings (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT NOT NULL,
  provider_id BIGINT NOT NULL,
  service_id BIGINT NOT NULL,
  address VARCHAR(255) NOT NULL,
  area VARCHAR(100) NOT NULL,
  formatted_address VARCHAR(255),
  latitude DECIMAL(10,7),
  longitude DECIMAL(10,7),
  booking_type ENUM('NORMAL','QUICK_SERVICE') NOT NULL,
  booking_date DATE,
  booking_time TIME,
  status ENUM('PENDING','ACCEPTED','REJECTED','ON_THE_WAY',
              'SERVICE_STARTED','AWAITING_COMPLETION','COMPLETED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  notes VARCHAR(500),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  INDEX idx_status (status),
  FOREIGN KEY (customer_id) REFERENCES customers(id),
  FOREIGN KEY (provider_id) REFERENCES providers(id),
  FOREIGN KEY (service_id) REFERENCES services(id)
);

CREATE TABLE service_otps (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  booking_id BIGINT NOT NULL UNIQUE,
  otp_hash VARCHAR(255) NOT NULL,
  encrypted_otp VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 5,
  verified BOOLEAN NOT NULL DEFAULT FALSE,
  verified_at TIMESTAMP NULL,
  blocked_until TIMESTAMP NULL,
  FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
);

CREATE TABLE reviews (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  booking_id BIGINT NOT NULL UNIQUE,         -- one review per completed booking
  customer_id BIGINT NOT NULL,
  provider_id BIGINT NOT NULL,
  rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  review VARCHAR(1000),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (booking_id) REFERENCES bookings(id),
  FOREIGN KEY (customer_id) REFERENCES customers(id),
  FOREIGN KEY (provider_id) REFERENCES providers(id)
);

INSERT INTO services (name, description, icon) VALUES
 ('Electrician','Wiring, fittings and electrical repairs','bi-lightning-charge'),
 ('Plumber','Leaks, taps, pipes and bathroom fittings','bi-droplet'),
 ('Mechanic','Bike and car repair and servicing','bi-wrench'),
 ('Cleaner','Home and office cleaning','bi-stars'),
 ('Barber','Haircut and grooming at home','bi-scissors'),
 ('Carpenter','Furniture repair and woodwork','bi-hammer'),
 ('AC/Fridge Repair','AC and refrigerator service','bi-snow'),
 ('Appliance Repair','Washing machine, TV, microwave and more','bi-tv'),
 ('Painter','Interior and exterior painting','bi-brush'),
 ('Dry Cleaner','Pickup and delivery dry cleaning','bi-basket');

-- The admin user is created by the backend on first start (Phase 2),
-- so the password is stored as a proper BCrypt hash.
