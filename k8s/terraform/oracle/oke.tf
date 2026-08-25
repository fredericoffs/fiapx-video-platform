data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

resource "oci_containerengine_cluster" "this" {
  compartment_id     = var.compartment_ocid
  name               = var.cluster_name
  vcn_id             = oci_core_vcn.this.id
  kubernetes_version = var.kubernetes_version
  type               = "BASIC_CLUSTER"

  endpoint_config {
    is_public_ip_enabled = true
    subnet_id             = oci_core_subnet.public.id
  }

  options {
    service_lb_subnet_ids = [oci_core_subnet.public.id]

    kubernetes_network_config {
      pods_cidr     = "10.244.0.0/16"
      services_cidr = "10.96.0.0/16"
    }
  }
}

# Ponto mais sensível deste módulo: lookup do OCID de imagem do node pool — confira
# `terraform plan` com atenção aqui antes de aplicar.
data "oci_containerengine_node_pool_option" "this" {
  node_pool_option_id = oci_containerengine_cluster.this.id
  compartment_id      = var.compartment_ocid
}

locals {
  a1_image_id = [
    for source in data.oci_containerengine_node_pool_option.this.sources :
    source.image_id if length(regexall("OKE-", source.source_name)) > 0
  ][0]
}

resource "oci_containerengine_node_pool" "a1" {
  compartment_id     = var.compartment_ocid
  cluster_id         = oci_containerengine_cluster.this.id
  name               = "${var.cluster_name}-a1-pool"
  kubernetes_version = var.kubernetes_version
  node_shape         = "VM.Standard.A1.Flex"

  node_shape_config {
    ocpus         = var.node_pool_ocpus
    memory_in_gbs = var.node_pool_memory_gb
  }

  node_source_details {
    source_type = "IMAGE"
    image_id    = local.a1_image_id
  }

  node_config_details {
    size = var.node_pool_size

    placement_configs {
      availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
      subnet_id            = oci_core_subnet.public.id
    }

    node_pool_pod_network_option_details {
      cni_type = "FLANNEL_OVERLAY"
    }
  }

  ssh_public_key = var.ssh_public_key != "" ? var.ssh_public_key : null
}
