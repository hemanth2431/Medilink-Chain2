-- ============================================================================
-- MEDILINKCHAIN - HIERARCHICAL ORGANIZATION, SUPER ADMIN, HOSPITAL ADMIN,
-- AND DOCTOR MANAGEMENT SYSTEM DATABASE SCHEMA
-- ============================================================================

-- Enable UUID extension if not enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. ORGANIZATIONS / HOSPITALS TABLE
CREATE TABLE IF NOT EXISTS public.organizations (
    id VARCHAR(64) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    code VARCHAR(32) UNIQUE NOT NULL,
    organization_name TEXT NOT NULL,
    name TEXT NOT NULL,
    address TEXT,
    contact TEXT,
    email TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. PROFILES TABLE (linked to auth.users if using Supabase Auth or mapped by UID)
CREATE TABLE IF NOT EXISTS public.profiles (
    id VARCHAR(128) PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    full_name TEXT,
    phone TEXT,
    role TEXT NOT NULL DEFAULT 'PATIENT' CHECK (role IN ('SUPER_ADMIN', 'HOSPITAL_ADMIN', 'ADMIN', 'DOCTOR', 'PATIENT')),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED', 'PENDING')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. PERMISSIONS TABLE
CREATE TABLE IF NOT EXISTS public.permissions (
    permission_name VARCHAR(64) PRIMARY KEY,
    description TEXT NOT NULL
);

INSERT INTO public.permissions (permission_name, description) VALUES
    ('HOSPITAL_CREATE', 'Create and register new hospital organizations'),
    ('HOSPITAL_MANAGE', 'Manage, edit, activate, or disable hospitals'),
    ('HOSPITAL_ADMIN_ASSIGN', 'Assign or replace primary hospital administrators'),
    ('ADMIN_CREATE', 'Create and invite new organization administrators'),
    ('ADMIN_DISABLE', 'Disable organization administrators'),
    ('ADMIN_ENABLE', 'Enable organization administrators'),
    ('ADMIN_REMOVE', 'Remove organization administrators'),
    ('DOCTOR_INVITE', 'Generate Doctor Registration IDs and invitations'),
    ('DOCTOR_VIEW', 'View doctor applications and profiles'),
    ('DOCTOR_APPROVE', 'Approve pending doctor registrations'),
    ('DOCTOR_REJECT', 'Reject pending doctor registrations with reason'),
    ('DOCTOR_DISABLE', 'Disable active doctor accounts'),
    ('DOCTOR_ENABLE', 'Reactivate disabled doctor accounts'),
    ('PATIENT_VIEW', 'View patient directory and basic profiles'),
    ('PATIENT_ENABLE', 'Enable patient access'),
    ('PATIENT_DISABLE', 'Disable patient access'),
    ('SYSTEM_VIEW', 'View real-time system metrics and monitoring'),
    ('AUDIT_VIEW', 'View organization audit trails and logs'),
    ('ORG_SETTINGS_MANAGE', 'Manage organization settings and details')
ON CONFLICT (permission_name) DO NOTHING;

-- 4. ROLE PERMISSIONS TABLE
CREATE TABLE IF NOT EXISTS public.role_permissions (
    role VARCHAR(32) NOT NULL,
    permission_name VARCHAR(64) NOT NULL REFERENCES public.permissions(permission_name) ON DELETE CASCADE,
    PRIMARY KEY (role, permission_name)
);

-- SUPER_ADMIN permissions (All permissions across all hospitals)
INSERT INTO public.role_permissions (role, permission_name) VALUES
    ('SUPER_ADMIN', 'HOSPITAL_CREATE'),
    ('SUPER_ADMIN', 'HOSPITAL_MANAGE'),
    ('SUPER_ADMIN', 'HOSPITAL_ADMIN_ASSIGN'),
    ('SUPER_ADMIN', 'ADMIN_CREATE'),
    ('SUPER_ADMIN', 'ADMIN_DISABLE'),
    ('SUPER_ADMIN', 'ADMIN_ENABLE'),
    ('SUPER_ADMIN', 'ADMIN_REMOVE'),
    ('SUPER_ADMIN', 'DOCTOR_INVITE'),
    ('SUPER_ADMIN', 'DOCTOR_VIEW'),
    ('SUPER_ADMIN', 'DOCTOR_APPROVE'),
    ('SUPER_ADMIN', 'DOCTOR_REJECT'),
    ('SUPER_ADMIN', 'DOCTOR_DISABLE'),
    ('SUPER_ADMIN', 'DOCTOR_ENABLE'),
    ('SUPER_ADMIN', 'PATIENT_VIEW'),
    ('SUPER_ADMIN', 'PATIENT_ENABLE'),
    ('SUPER_ADMIN', 'PATIENT_DISABLE'),
    ('SUPER_ADMIN', 'SYSTEM_VIEW'),
    ('SUPER_ADMIN', 'AUDIT_VIEW'),
    ('SUPER_ADMIN', 'ORG_SETTINGS_MANAGE')
ON CONFLICT DO NOTHING;

-- HOSPITAL_ADMIN permissions (Restricted to their assigned hospital)
INSERT INTO public.role_permissions (role, permission_name) VALUES
    ('HOSPITAL_ADMIN', 'DOCTOR_INVITE'),
    ('HOSPITAL_ADMIN', 'DOCTOR_VIEW'),
    ('HOSPITAL_ADMIN', 'DOCTOR_APPROVE'),
    ('HOSPITAL_ADMIN', 'DOCTOR_REJECT'),
    ('HOSPITAL_ADMIN', 'DOCTOR_DISABLE'),
    ('HOSPITAL_ADMIN', 'DOCTOR_ENABLE'),
    ('HOSPITAL_ADMIN', 'PATIENT_VIEW'),
    ('HOSPITAL_ADMIN', 'PATIENT_ENABLE'),
    ('HOSPITAL_ADMIN', 'PATIENT_DISABLE'),
    ('HOSPITAL_ADMIN', 'SYSTEM_VIEW'),
    ('HOSPITAL_ADMIN', 'AUDIT_VIEW')
ON CONFLICT DO NOTHING;

-- Legacy ADMIN role permissions
INSERT INTO public.role_permissions (role, permission_name) VALUES
    ('ADMIN', 'DOCTOR_INVITE'),
    ('ADMIN', 'DOCTOR_VIEW'),
    ('ADMIN', 'DOCTOR_APPROVE'),
    ('ADMIN', 'DOCTOR_REJECT'),
    ('ADMIN', 'PATIENT_VIEW'),
    ('ADMIN', 'PATIENT_ENABLE'),
    ('ADMIN', 'PATIENT_DISABLE'),
    ('ADMIN', 'SYSTEM_VIEW'),
    ('ADMIN', 'AUDIT_VIEW')
ON CONFLICT DO NOTHING;

-- 5. ORGANIZATION MEMBERS TABLE
CREATE TABLE IF NOT EXISTS public.organization_members (
    id VARCHAR(64) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    organization_id VARCHAR(64) NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
    user_id VARCHAR(128) NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('SUPER_ADMIN', 'HOSPITAL_ADMIN', 'ADMIN', 'DOCTOR', 'PATIENT')),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED', 'PENDING')),
    is_primary_admin BOOLEAN NOT NULL DEFAULT FALSE,
    full_name TEXT,
    email TEXT,
    phone TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(128),
    admin_permissions TEXT[] DEFAULT '{}',
    CONSTRAINT unique_user_org UNIQUE (organization_id, user_id)
);

-- Partial Unique Index: Exactly one active primary admin per organization
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_primary_admin
ON public.organization_members (organization_id)
WHERE is_primary_admin = TRUE AND status = 'ACTIVE';

-- 6. DOCTOR INVITATIONS TABLE
CREATE TABLE IF NOT EXISTS public.doctor_invitations (
    id VARCHAR(64) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    organization_id VARCHAR(64) NOT NULL REFERENCES public.organizations(id) ON DELETE CASCADE,
    registration_id TEXT UNIQUE NOT NULL,
    doctor_name TEXT,
    specialization TEXT,
    email TEXT,
    phone TEXT,
    medical_registration_number TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'USED', 'EXPIRED', 'CANCELLED')),
    created_by VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. DOCTOR PROFILES TABLE
CREATE TABLE IF NOT EXISTS public.doctor_profiles (
    id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    organization_id VARCHAR(64) REFERENCES public.organizations(id) ON DELETE SET NULL,
    registration_id TEXT NOT NULL,
    doctor_registration_id TEXT,
    full_name TEXT,
    name TEXT,
    email TEXT NOT NULL,
    phone TEXT,
    hospital_name TEXT NOT NULL,
    specialization TEXT DEFAULT 'General Physician',
    medical_registration_number TEXT,
    verification_status TEXT NOT NULL DEFAULT 'PENDING' CHECK (verification_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    account_status TEXT NOT NULL DEFAULT 'PENDING' CHECK (account_status IN ('ACTIVE', 'DISABLED', 'PENDING', 'REJECTED')),
    status TEXT NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verified_at TIMESTAMPTZ,
    verified_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 8. PATIENT PROFILES TABLE
CREATE TABLE IF NOT EXISTS public.patient_profiles (
    user_id VARCHAR(128) PRIMARY KEY,
    organization_id VARCHAR(64) REFERENCES public.organizations(id) ON DELETE SET NULL,
    patient_id TEXT UNIQUE,
    full_name TEXT,
    email TEXT NOT NULL,
    contact_number TEXT,
    blood_group TEXT,
    age INT,
    gender TEXT,
    blood_pressure TEXT,
    diabetes TEXT,
    emergency_contact_name TEXT,
    emergency_contact_number TEXT,
    address TEXT,
    profile_complete BOOLEAN NOT NULL DEFAULT FALSE,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 9. MEDICAL RECORDS TABLE
CREATE TABLE IF NOT EXISTS public.medical_records (
    id VARCHAR(64) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    patient_id VARCHAR(128) NOT NULL,
    organization_id VARCHAR(64) REFERENCES public.organizations(id) ON DELETE SET NULL,
    record_type TEXT NOT NULL,
    record_title TEXT,
    date_of_record TEXT,
    hospital_clinic TEXT,
    description TEXT,
    file_name TEXT,
    file_type TEXT,
    file_size TEXT,
    file_url TEXT,
    storage_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 10. AUDIT LOGS TABLE (Append-only)
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id VARCHAR(64) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    organization_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    actor_email TEXT,
    actor_role TEXT,
    action TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 11. HEALTH ASSESSMENTS TABLE
CREATE TABLE IF NOT EXISTS public.health_assessments (
    id VARCHAR(64) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    user_id VARCHAR(128) NOT NULL,
    assessment_date TEXT NOT NULL,
    age INT NOT NULL,
    gender TEXT NOT NULL,
    height_cm NUMERIC NOT NULL,
    weight_kg NUMERIC NOT NULL,
    bmi NUMERIC NOT NULL,
    bmi_category TEXT NOT NULL,
    activity_level TEXT NOT NULL,
    systolic_bp INT NOT NULL,
    diastolic_bp INT NOT NULL,
    fasting_blood_sugar INT NOT NULL,
    post_meal_blood_sugar INT DEFAULT 0,
    existing_condition TEXT NOT NULL,
    food_preference TEXT NOT NULL,
    health_goal TEXT NOT NULL,
    maintenance_calories INT NOT NULL,
    goal_calories INT NOT NULL,
    overall_indicator TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.health_risk_assessments (
    id VARCHAR(64) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    user_id VARCHAR(128) NOT NULL,
    age INT NOT NULL,
    gender TEXT NOT NULL,
    height_cm NUMERIC NOT NULL,
    weight_kg NUMERIC NOT NULL,
    bmi NUMERIC NOT NULL,
    systolic_bp INT,
    diastolic_bp INT,
    fasting_glucose INT,
    smoking TEXT,
    alcohol TEXT,
    physical_activity TEXT,
    family_history TEXT,
    risk_score INT NOT NULL,
    risk_category TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 12. MEDICATIONS TABLE
CREATE TABLE IF NOT EXISTS public.medications (
    id VARCHAR(64) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    user_id VARCHAR(128) NOT NULL,
    medicine_name TEXT NOT NULL,
    dosage TEXT NOT NULL,
    frequency TEXT NOT NULL,
    scheduled_times TEXT[] DEFAULT '{}',
    times TEXT[] DEFAULT '{}',
    start_date TEXT NOT NULL,
    end_date TEXT NOT NULL,
    notes TEXT,
    tablet_quantity INT NOT NULL DEFAULT 1,
    remaining_tablets INT NOT NULL DEFAULT 1,
    refill_threshold INT NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 13. MEDICATION HISTORY TABLE
CREATE TABLE IF NOT EXISTS public.medication_history (
    id VARCHAR(64) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    medication_id VARCHAR(64) NOT NULL REFERENCES public.medications(id) ON DELETE CASCADE,
    user_id VARCHAR(128) NOT NULL,
    medicine_name TEXT,
    dosage TEXT,
    scheduled_time TEXT,
    action TEXT NOT NULL CHECK (action IN ('TAKEN', 'SKIPPED', 'REFILLED')),
    quantity_used INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 14. PRESCRIPTIONS TABLE
CREATE TABLE IF NOT EXISTS public.prescriptions (
    id VARCHAR(64) PRIMARY KEY DEFAULT uuid_generate_v4()::text,
    user_id VARCHAR(128) NOT NULL,
    title TEXT NOT NULL,
    doctor_name TEXT NOT NULL,
    hospital_name TEXT NOT NULL,
    prescription_date TEXT NOT NULL,
    file_path TEXT NOT NULL,
    file_type TEXT NOT NULL,
    file_size TEXT,
    file_url TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for Fast Lookups
CREATE INDEX IF NOT EXISTS idx_organizations_code ON public.organizations(code);
CREATE INDEX IF NOT EXISTS idx_organizations_status ON public.organizations(status);
CREATE INDEX IF NOT EXISTS idx_org_members_user ON public.organization_members(user_id);
CREATE INDEX IF NOT EXISTS idx_org_members_org ON public.organization_members(organization_id);
CREATE INDEX IF NOT EXISTS idx_org_members_role ON public.organization_members(role);
CREATE INDEX IF NOT EXISTS idx_doc_invitations_reg_id ON public.doctor_invitations(registration_id);
CREATE INDEX IF NOT EXISTS idx_doc_invitations_org ON public.doctor_invitations(organization_id);
CREATE INDEX IF NOT EXISTS idx_doc_invitations_status ON public.doctor_invitations(status);
CREATE INDEX IF NOT EXISTS idx_doc_verification ON public.doctor_profiles(verification_status);
CREATE INDEX IF NOT EXISTS idx_doc_org ON public.doctor_profiles(organization_id);
CREATE INDEX IF NOT EXISTS idx_doc_reg_id ON public.doctor_profiles(doctor_registration_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_org_time ON public.audit_logs(organization_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON public.audit_logs(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON public.audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_med_records_patient ON public.medical_records(patient_id);
CREATE INDEX IF NOT EXISTS idx_medications_user ON public.medications(user_id);
CREATE INDEX IF NOT EXISTS idx_prescriptions_user ON public.prescriptions(user_id);

-- ============================================================================
-- STORED PROCEDURES & ATOMIC FUNCTIONS
-- ============================================================================

-- Function: Generate safe, globally unique Doctor Registration ID
-- Format: DOC-{HOSPITAL_CODE}-{YEAR}-{SEQUENCE} (e.g. DOC-ABC-2026-0001)
CREATE OR REPLACE FUNCTION public.generate_doctor_registration_id(
    p_org_id VARCHAR(64),
    p_doctor_name TEXT,
    p_specialization TEXT,
    p_email TEXT,
    p_phone TEXT,
    p_med_reg_num TEXT,
    p_created_by VARCHAR(128)
)
RETURNS JSON AS $$
DECLARE
    v_hosp_code VARCHAR(32);
    v_year TEXT;
    v_seq INT;
    v_reg_id TEXT;
    v_invitation_id VARCHAR(64);
    v_result JSON;
BEGIN
    -- 1. Get hospital code and verify hospital exists
    SELECT UPPER(code) INTO v_hosp_code
    FROM public.organizations
    WHERE id = p_org_id AND status = 'ACTIVE';

    IF v_hosp_code IS NULL OR v_hosp_code = '' THEN
        RAISE EXCEPTION 'Active organization not found for ID: %', p_org_id;
    END IF;

    v_year := TO_CHAR(NOW(), 'YYYY');

    -- 2. Count existing invitations for this hospital & year to generate sequence atomically
    SELECT COUNT(*) + 1 INTO v_seq
    FROM public.doctor_invitations
    WHERE organization_id = p_org_id
      AND registration_id LIKE ('DOC-' || v_hosp_code || '-' || v_year || '-%');

    -- 3. Construct unique registration ID
    v_reg_id := 'DOC-' || v_hosp_code || '-' || v_year || '-' || LPAD(v_seq::text, 4, '0');

    -- 4. Check if duplicate exists (collision check loop)
    WHILE EXISTS (SELECT 1 FROM public.doctor_invitations WHERE registration_id = v_reg_id) LOOP
        v_seq := v_seq + 1;
        v_reg_id := 'DOC-' || v_hosp_code || '-' || v_year || '-' || LPAD(v_seq::text, 4, '0');
    END LOOP;

    -- 5. Insert active invitation
    INSERT INTO public.doctor_invitations (
        organization_id,
        registration_id,
        doctor_name,
        specialization,
        email,
        phone,
        medical_registration_number,
        status,
        created_by,
        expires_at,
        created_at
    ) VALUES (
        p_org_id,
        v_reg_id,
        p_doctor_name,
        p_specialization,
        p_email,
        p_phone,
        p_med_reg_num,
        'ACTIVE',
        p_created_by,
        NOW() + INTERVAL '30 days',
        NOW()
    ) RETURNING id INTO v_invitation_id;

    -- 6. Log in audit trail
    INSERT INTO public.audit_logs (
        organization_id, actor_id, actor_role, action, target_type, target_id, description, created_at
    ) VALUES (
        p_org_id,
        p_created_by,
        'HOSPITAL_ADMIN',
        'DOCTOR_INVITATION_CREATED',
        'DOCTOR_INVITATION',
        v_reg_id,
        'Doctor registration ID generated: ' || v_reg_id || ' for ' || COALESCE(p_doctor_name, 'New Doctor'),
        NOW()
    );

    v_result := json_build_object(
        'success', true,
        'registration_id', v_reg_id,
        'invitation_id', v_invitation_id,
        'organization_id', p_org_id,
        'hospital_code', v_hosp_code
    );

    RETURN v_result;
END;
$$ LANGUAGE plpgsql;

-- Function: Validate Doctor Registration ID during signup
CREATE OR REPLACE FUNCTION public.validate_doctor_invitation(
    p_registration_id TEXT
)
RETURNS JSON AS $$
DECLARE
    v_invitation RECORD;
    v_org RECORD;
    v_result JSON;
BEGIN
    SELECT * INTO v_invitation
    FROM public.doctor_invitations
    WHERE registration_id = UPPER(TRIM(p_registration_id));

    IF NOT FOUND THEN
        RETURN json_build_object('valid', false, 'error', 'Invalid Doctor Registration ID.');
    END IF;

    IF v_invitation.status = 'USED' THEN
        RETURN json_build_object('valid', false, 'error', 'This Doctor Registration ID has already been used.');
    END IF;

    IF v_invitation.status = 'CANCELLED' THEN
        RETURN json_build_object('valid', false, 'error', 'This Doctor Registration ID has been cancelled.');
    END IF;

    IF v_invitation.expires_at IS NOT NULL AND v_invitation.expires_at < NOW() THEN
        RETURN json_build_object('valid', false, 'error', 'This Doctor Registration ID has expired.');
    END IF;

    SELECT * INTO v_org
    FROM public.organizations
    WHERE id = v_invitation.organization_id;

    IF NOT FOUND OR v_org.status <> 'ACTIVE' THEN
        RETURN json_build_object('valid', false, 'error', 'Associated hospital is inactive or disabled.');
    END IF;

    v_result := json_build_object(
        'valid', true,
        'registration_id', v_invitation.registration_id,
        'organization_id', v_org.id,
        'hospital_name', COALESCE(v_org.name, v_org.organization_name),
        'hospital_code', v_org.code,
        'doctor_name', v_invitation.doctor_name,
        'specialization', v_invitation.specialization,
        'email', v_invitation.email,
        'phone', v_invitation.phone,
        'medical_registration_number', v_invitation.medical_registration_number
    );

    RETURN v_result;
END;
$$ LANGUAGE plpgsql;

-- Function: Assign Primary Hospital Admin (replaces existing if any)
CREATE OR REPLACE FUNCTION public.assign_hospital_primary_admin(
    p_org_id VARCHAR(64),
    p_user_id VARCHAR(128),
    p_full_name TEXT,
    p_email TEXT,
    p_phone TEXT,
    p_actor_id VARCHAR(128),
    p_actor_email TEXT
)
RETURNS JSON AS $$
DECLARE
    v_old_admin RECORD;
    v_member_id VARCHAR(64);
    v_result JSON;
BEGIN
    -- 1. Check if an active primary admin already exists for this hospital
    SELECT * INTO v_old_admin
    FROM public.organization_members
    WHERE organization_id = p_org_id
      AND is_primary_admin = TRUE
      AND status = 'ACTIVE'
      AND user_id <> p_user_id;

    IF FOUND THEN
        -- Demote / Disable previous admin
        UPDATE public.organization_members
        SET is_primary_admin = FALSE,
            status = 'DISABLED',
            updated_at = NOW()
        WHERE id = v_old_admin.id;

        -- Record replacement in audit log
        INSERT INTO public.audit_logs (
            organization_id, actor_id, actor_email, actor_role, action, target_type, target_id, description, created_at
        ) VALUES (
            p_org_id,
            p_actor_id,
            p_actor_email,
            'SUPER_ADMIN',
            'HOSPITAL_ADMIN_REMOVED',
            'ADMIN',
            v_old_admin.user_id,
            'Previous primary Hospital Admin (' || COALESCE(v_old_admin.email, v_old_admin.user_id) || ') replaced.',
            NOW()
        );
    END IF;

    -- 2. Create or update profile
    INSERT INTO public.profiles (id, email, full_name, phone, role, status, created_at, updated_at)
    VALUES (p_user_id, p_email, p_full_name, p_phone, 'HOSPITAL_ADMIN', 'ACTIVE', NOW(), NOW())
    ON CONFLICT (id) DO UPDATE
    SET role = 'HOSPITAL_ADMIN', status = 'ACTIVE', full_name = EXCLUDED.full_name, phone = EXCLUDED.phone;

    -- 3. Upsert organization member
    INSERT INTO public.organization_members (
        organization_id, user_id, role, status, is_primary_admin, full_name, email, phone, created_at, created_by, admin_permissions
    ) VALUES (
        p_org_id,
        p_user_id,
        'HOSPITAL_ADMIN',
        'ACTIVE',
        TRUE,
        p_full_name,
        p_email,
        p_phone,
        NOW(),
        p_actor_id,
        ARRAY['DOCTOR_INVITE','DOCTOR_VIEW','DOCTOR_APPROVE','DOCTOR_REJECT','DOCTOR_DISABLE','DOCTOR_ENABLE','PATIENT_VIEW','PATIENT_ENABLE','PATIENT_DISABLE','SYSTEM_VIEW','AUDIT_VIEW']
    )
    ON CONFLICT (organization_id, user_id) DO UPDATE
    SET role = 'HOSPITAL_ADMIN', status = 'ACTIVE', is_primary_admin = TRUE, full_name = EXCLUDED.full_name, phone = EXCLUDED.phone, updated_at = NOW()
    RETURNING id INTO v_member_id;

    -- 4. Record new assignment in audit log
    INSERT INTO public.audit_logs (
        organization_id, actor_id, actor_email, actor_role, action, target_type, target_id, description, created_at
    ) VALUES (
        p_org_id,
        p_actor_id,
        p_actor_email,
        'SUPER_ADMIN',
        'HOSPITAL_ADMIN_ASSIGNED',
        'ADMIN',
        p_user_id,
        'Primary Hospital Admin assigned: ' || p_full_name || ' (' || p_email || ')',
        NOW()
    );

    v_result := json_build_object(
        'success', true,
        'member_id', v_member_id,
        'organization_id', p_org_id,
        'user_id', p_user_id
    );

    RETURN v_result;
END;
$$ LANGUAGE plpgsql;

-- Super Admin protection trigger
CREATE OR REPLACE FUNCTION public.check_super_admin_protection()
RETURNS TRIGGER AS $$
DECLARE
    active_super_admin_count INT;
BEGIN
    IF (TG_OP = 'UPDATE') THEN
        IF (OLD.role = 'SUPER_ADMIN' AND (NEW.role <> 'SUPER_ADMIN' OR NEW.status <> 'ACTIVE')) THEN
            SELECT COUNT(*) INTO active_super_admin_count
            FROM public.organization_members
            WHERE role = 'SUPER_ADMIN'
              AND status = 'ACTIVE'
              AND id <> OLD.id;

            IF (active_super_admin_count < 1) THEN
                RAISE EXCEPTION 'At least one active Super Admin must remain in the organization.';
            END IF;
        END IF;
    ELSIF (TG_OP = 'DELETE') THEN
        IF (OLD.role = 'SUPER_ADMIN' AND OLD.status = 'ACTIVE') THEN
            SELECT COUNT(*) INTO active_super_admin_count
            FROM public.organization_members
            WHERE role = 'SUPER_ADMIN'
              AND status = 'ACTIVE'
              AND id <> OLD.id;

            IF (active_super_admin_count < 1) THEN
                RAISE EXCEPTION 'At least one active Super Admin must remain in the organization.';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_protect_last_super_admin ON public.organization_members;
CREATE TRIGGER trg_protect_last_super_admin
BEFORE UPDATE OR DELETE ON public.organization_members
FOR EACH ROW EXECUTE FUNCTION public.check_super_admin_protection();

-- ============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================================================
ALTER TABLE public.organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.organization_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.doctor_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.doctor_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.patient_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.medical_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.health_risk_assessments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.medications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.medication_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.prescriptions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read organizations"
    ON public.organizations FOR SELECT USING (true);

CREATE POLICY "Allow read organization_members"
    ON public.organization_members FOR SELECT USING (true);

CREATE POLICY "Allow insert organization_members"
    ON public.organization_members FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow update organization_members"
    ON public.organization_members FOR UPDATE USING (true);

CREATE POLICY "Allow read doctor_invitations"
    ON public.doctor_invitations FOR SELECT USING (true);

CREATE POLICY "Allow insert update doctor_invitations"
    ON public.doctor_invitations FOR ALL USING (true);

CREATE POLICY "Allow read doctor_profiles"
    ON public.doctor_profiles FOR SELECT USING (true);

CREATE POLICY "Allow insert update doctor_profiles"
    ON public.doctor_profiles FOR ALL USING (true);

-- ============================================================================
-- STRICT ROW LEVEL SECURITY (RLS) POLICIES — ACCOUNT DATA ISOLATION
-- ============================================================================

-- 1. PATIENT PROFILES
CREATE POLICY "Patient profiles select policy"
    ON public.patient_profiles FOR SELECT
    USING (
        user_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('DOCTOR', 'HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

CREATE POLICY "Patient profiles insert policy"
    ON public.patient_profiles FOR INSERT
    WITH CHECK (
        user_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

CREATE POLICY "Patient profiles update policy"
    ON public.patient_profiles FOR UPDATE
    USING (
        user_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

CREATE POLICY "Patient profiles delete policy"
    ON public.patient_profiles FOR DELETE
    USING (
        EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

-- 2. MEDICAL RECORDS
CREATE POLICY "Medical records select policy"
    ON public.medical_records FOR SELECT
    USING (
        patient_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('DOCTOR', 'HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

CREATE POLICY "Medical records insert policy"
    ON public.medical_records FOR INSERT
    WITH CHECK (
        patient_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('DOCTOR', 'HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

CREATE POLICY "Medical records update policy"
    ON public.medical_records FOR UPDATE
    USING (
        patient_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

CREATE POLICY "Medical records delete policy"
    ON public.medical_records FOR DELETE
    USING (
        patient_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

-- 3. AUDIT LOGS
CREATE POLICY "Audit logs insert policy"
    ON public.audit_logs FOR INSERT
    WITH CHECK (true);

CREATE POLICY "Audit logs select policy"
    ON public.audit_logs FOR SELECT
    USING (
        actor_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN', 'DOCTOR')
        )
    );

-- 4. HEALTH ASSESSMENTS
CREATE POLICY "Health assessments select policy"
    ON public.health_assessments FOR SELECT
    USING (
        user_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('DOCTOR', 'HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

CREATE POLICY "Health assessments insert policy"
    ON public.health_assessments FOR INSERT
    WITH CHECK (user_id = auth.uid()::text);

CREATE POLICY "Health assessments update policy"
    ON public.health_assessments FOR UPDATE
    USING (user_id = auth.uid()::text);

CREATE POLICY "Health assessments delete policy"
    ON public.health_assessments FOR DELETE
    USING (user_id = auth.uid()::text);

-- 5. HEALTH RISK ASSESSMENTS
CREATE POLICY "Health risk assessments select policy"
    ON public.health_risk_assessments FOR SELECT
    USING (
        user_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('DOCTOR', 'HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

CREATE POLICY "Health risk assessments insert policy"
    ON public.health_risk_assessments FOR INSERT
    WITH CHECK (user_id = auth.uid()::text);

CREATE POLICY "Health risk assessments update policy"
    ON public.health_risk_assessments FOR UPDATE
    USING (user_id = auth.uid()::text);

CREATE POLICY "Health risk assessments delete policy"
    ON public.health_risk_assessments FOR DELETE
    USING (user_id = auth.uid()::text);

-- 6. MEDICATIONS
CREATE POLICY "Medications select policy"
    ON public.medications FOR SELECT
    USING (
        user_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('DOCTOR', 'HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

CREATE POLICY "Medications insert policy"
    ON public.medications FOR INSERT
    WITH CHECK (
        user_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('DOCTOR', 'HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

CREATE POLICY "Medications update policy"
    ON public.medications FOR UPDATE
    USING (user_id = auth.uid()::text);

CREATE POLICY "Medications delete policy"
    ON public.medications FOR DELETE
    USING (user_id = auth.uid()::text);

-- 7. MEDICATION HISTORY
CREATE POLICY "Medication history select policy"
    ON public.medication_history FOR SELECT
    USING (
        user_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('DOCTOR', 'HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

CREATE POLICY "Medication history insert policy"
    ON public.medication_history FOR INSERT
    WITH CHECK (user_id = auth.uid()::text);

CREATE POLICY "Medication history update policy"
    ON public.medication_history FOR UPDATE
    USING (user_id = auth.uid()::text);

CREATE POLICY "Medication history delete policy"
    ON public.medication_history FOR DELETE
    USING (user_id = auth.uid()::text);

-- 8. PRESCRIPTIONS
CREATE POLICY "Prescriptions select policy"
    ON public.prescriptions FOR SELECT
    USING (
        user_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('DOCTOR', 'HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

CREATE POLICY "Prescriptions insert policy"
    ON public.prescriptions FOR INSERT
    WITH CHECK (
        user_id = auth.uid()::text
        OR EXISTS (
            SELECT 1 FROM public.profiles
            WHERE id = auth.uid()::text
            AND role IN ('DOCTOR', 'HOSPITAL_ADMIN', 'SUPER_ADMIN')
        )
    );

CREATE POLICY "Prescriptions update policy"
    ON public.prescriptions FOR UPDATE
    USING (user_id = auth.uid()::text);

CREATE POLICY "Prescriptions delete policy"
    ON public.prescriptions FOR DELETE
    USING (user_id = auth.uid()::text);

-- ============================================================================
-- SUPABASE STORAGE BUCKET & ROW LEVEL SECURITY FOR MEDICAL DOCUMENTS
-- ============================================================================
INSERT INTO storage.buckets (id, name, public)
VALUES ('medical-records', 'medical-records', false)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY "Storage medical records select policy"
    ON storage.objects FOR SELECT
    USING (
        bucket_id = 'medical-records'
        AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR EXISTS (
                SELECT 1 FROM public.profiles
                WHERE id = auth.uid()::text
                AND role IN ('DOCTOR', 'HOSPITAL_ADMIN', 'SUPER_ADMIN')
            )
        )
    );

CREATE POLICY "Storage medical records insert policy"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'medical-records'
        AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR EXISTS (
                SELECT 1 FROM public.profiles
                WHERE id = auth.uid()::text
                AND role IN ('DOCTOR', 'HOSPITAL_ADMIN', 'SUPER_ADMIN')
            )
        )
    );

CREATE POLICY "Storage medical records update policy"
    ON storage.objects FOR UPDATE
    USING (
        bucket_id = 'medical-records'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "Storage medical records delete policy"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'medical-records'
        AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR EXISTS (
                SELECT 1 FROM public.profiles
                WHERE id = auth.uid()::text
                AND role IN ('HOSPITAL_ADMIN', 'SUPER_ADMIN')
            )
        )
    );

-- ============================================================================
-- INITIAL SUPER ADMIN SETUP / BOOTSTRAP FUNCTION
-- ============================================================================
CREATE OR REPLACE FUNCTION public.bootstrap_organization_and_super_admin(
    p_org_id TEXT,
    p_org_name TEXT,
    p_org_code TEXT,
    p_super_admin_uid TEXT,
    p_super_admin_email TEXT,
    p_super_admin_name TEXT
)
RETURNS JSON AS $$
DECLARE
    v_member_id TEXT;
    v_result JSON;
BEGIN
    INSERT INTO public.organizations (id, code, organization_name, name, status, created_at)
    VALUES (p_org_id, UPPER(COALESCE(p_org_code, 'MAIN')), p_org_name, p_org_name, 'ACTIVE', NOW())
    ON CONFLICT (id) DO UPDATE
    SET organization_name = EXCLUDED.organization_name,
        name = EXCLUDED.name,
        code = EXCLUDED.code;

    INSERT INTO public.profiles (id, email, full_name, role, status, created_at)
    VALUES (p_super_admin_uid, p_super_admin_email, p_super_admin_name, 'SUPER_ADMIN', 'ACTIVE', NOW())
    ON CONFLICT (id) DO UPDATE
    SET role = 'SUPER_ADMIN', status = 'ACTIVE';

    INSERT INTO public.organization_members (organization_id, user_id, role, status, is_primary_admin, created_at, created_by, admin_permissions)
    VALUES (
        p_org_id,
        p_super_admin_uid,
        'SUPER_ADMIN',
        'ACTIVE',
        TRUE,
        NOW(),
        p_super_admin_uid,
        ARRAY['HOSPITAL_CREATE','HOSPITAL_MANAGE','HOSPITAL_ADMIN_ASSIGN','ADMIN_CREATE','ADMIN_DISABLE','ADMIN_ENABLE','ADMIN_REMOVE','DOCTOR_INVITE','DOCTOR_VIEW','DOCTOR_APPROVE','DOCTOR_REJECT','DOCTOR_DISABLE','DOCTOR_ENABLE','PATIENT_VIEW','PATIENT_ENABLE','PATIENT_DISABLE','SYSTEM_VIEW','AUDIT_VIEW','ORG_SETTINGS_MANAGE']
    )
    ON CONFLICT (organization_id, user_id) DO UPDATE
    SET role = 'SUPER_ADMIN', status = 'ACTIVE', is_primary_admin = TRUE
    RETURNING id INTO v_member_id;

    INSERT INTO public.audit_logs (organization_id, actor_id, actor_email, actor_role, action, target_type, target_id, description, created_at)
    VALUES (
        p_org_id,
        p_super_admin_uid,
        p_super_admin_email,
        'SUPER_ADMIN',
        'ADMIN_CREATED',
        'ADMIN',
        p_super_admin_uid,
        'Initial Super Admin setup completed.',
        NOW()
    );

    v_result := json_build_object(
        'success', true,
        'organization_id', p_org_id,
        'super_admin_uid', p_super_admin_uid,
        'membership_id', v_member_id
    );

    RETURN v_result;
END;
$$ LANGUAGE plpgsql;


-- ============================================================================
-- 18. DIRECT LOGIN & AUTOMATIC USER CONFIRMATION (NO EMAIL VERIFICATION GATING)
-- ============================================================================
-- Automatically confirms newly created users in auth.users so direct login
-- using registered email and password functions immediately without email links.

CREATE OR REPLACE FUNCTION public.auto_confirm_auth_user()
RETURNS TRIGGER AS $$
BEGIN
    NEW.email_confirmed_at := COALESCE(NEW.email_confirmed_at, NOW());
    NEW.confirmed_at := COALESCE(NEW.confirmed_at, NOW());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS tr_auto_confirm_auth_user ON auth.users;
CREATE TRIGGER tr_auto_confirm_auth_user
BEFORE INSERT OR UPDATE ON auth.users
FOR EACH ROW
EXECUTE FUNCTION public.auto_confirm_auth_user();

-- Confirm all existing unconfirmed accounts
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'auth' AND table_name = 'users') THEN
        UPDATE auth.users
        SET email_confirmed_at = COALESCE(email_confirmed_at, NOW()),
            confirmed_at = COALESCE(confirmed_at, NOW())
        WHERE email_confirmed_at IS NULL;
    END IF;
END $$;
