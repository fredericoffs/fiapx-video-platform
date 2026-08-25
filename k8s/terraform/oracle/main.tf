# Cluster OKE (BASIC_CLUSTER, control plane gratuito) + node pool Ampere A1 —
# Always Free. Ver ADR-012 (docs/architecture/hld-lld-adr-rfc.md).
terraform {
  required_version = ">= 1.7"
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 6.0"
    }
  }
}

provider "oci" {
  tenancy_ocid = var.tenancy_ocid
  user_ocid    = var.user_ocid
  fingerprint  = var.fingerprint
  private_key  = var.private_key_pem
  region       = var.region
}
