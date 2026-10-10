-- No patient data is seeded or copied. Patient demographics are stored only as authenticated ciphertext.
ALTER TABLE sys_dept ADD UNIQUE KEY uk_dept_owner(institution_id,id);

CREATE TABLE lab_patient (
    id BIGINT NOT NULL AUTO_INCREMENT,
    institution_id BIGINT NOT NULL,
    number_index CHAR(64) NOT NULL COMMENT 'Institution-bound HMAC, never plaintext patient number',
    encrypted_profile TEXT NOT NULL COMMENT 'Versioned AES-GCM ciphertext; includes number/name/sex/birth date',
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(id),
    UNIQUE KEY uk_lab_patient_number(institution_id,number_index),
    UNIQUE KEY uk_lab_patient_owner(institution_id,id),
    CONSTRAINT fk_lab_patient_institution FOREIGN KEY(institution_id) REFERENCES institution(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lab_patient_department (
    id BIGINT NOT NULL AUTO_INCREMENT,
    institution_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY(id),
    UNIQUE KEY uk_lab_patient_department(institution_id,patient_id,department_id),
    KEY idx_lab_patient_dept_access(institution_id,department_id,active,patient_id),
    CONSTRAINT fk_lab_patient_dept_patient FOREIGN KEY(institution_id,patient_id) REFERENCES lab_patient(institution_id,id),
    CONSTRAINT fk_lab_patient_dept_department FOREIGN KEY(institution_id,department_id) REFERENCES sys_dept(institution_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
