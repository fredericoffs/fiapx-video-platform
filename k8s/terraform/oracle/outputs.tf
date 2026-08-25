output "cluster_id" {
  value = oci_containerengine_cluster.this.id
}

output "kubeconfig_command" {
  value = "oci ce cluster create-kubeconfig --cluster-id ${oci_containerengine_cluster.this.id} --file $HOME/.kube/config --region ${var.region} --token-version 2.0.0"
}

output "node_pool_id" {
  value = oci_containerengine_node_pool.a1.id
}
