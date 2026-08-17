package com.fiapx.notificationworker;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * notification-worker ainda é um esqueleto (só a classe de bootstrap Spring Boot). As regras
 * abaixo espelham a convenção hexagonal já adotada em video-api/video-worker e usam
 * allowEmptyShould(true) para não falhar enquanto os pacotes domain/application/infrastructure
 * não existem — passam a valer automaticamente assim que o serviço for implementado.
 */
@AnalyzeClasses(packages = "com.fiapx.notificationworker", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String DOMAIN = "..notificationworker.domain..";
  private static final String APPLICATION = "..notificationworker.application..";
  private static final String INFRASTRUCTURE = "..notificationworker.infrastructure..";
  private static final String INFRA_CONFIG = "..notificationworker.infrastructure.config..";
  private static final String DOMAIN_PORT = "..notificationworker.domain.port..";

  @ArchTest
  static final ArchRule dominio_nao_depende_de_application =
      noClasses().that().resideInAPackage(DOMAIN)
          .should().dependOnClassesThat().resideInAPackage(APPLICATION)
          .as("o domínio é o núcleo da arquitetura hexagonal e não pode conhecer a camada de aplicação")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule dominio_nao_depende_de_infrastructure =
      noClasses().that().resideInAPackage(DOMAIN)
          .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE)
          .as("o domínio não pode depender de adapters (regra da dependência: setas sempre apontam para dentro)")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule dominio_e_livre_de_frameworks =
      noClasses().that().resideInAPackage(DOMAIN)
          .should().dependOnClassesThat().resideInAnyPackage(
              "org.springframework..", "jakarta.persistence..", "jakarta.validation..",
              "tools.jackson..", "com.fasterxml.jackson..", "org.springframework.amqp..")
          .as("o domínio deve ser Plain Java, sem acoplamento a frameworks (persistence/framework ignorance)")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule application_nao_depende_de_adapters_de_infraestrutura =
      noClasses().that().resideInAPackage(APPLICATION)
          .should().dependOnClassesThat(resideInAPackage(INFRASTRUCTURE).and(not(resideInAPackage(INFRA_CONFIG))))
          .as("casos de uso dependem apenas de portas do domínio; infrastructure.config (records de "
              + "@ConfigurationProperties) é a única exceção tolerada, por não conter lógica de adapter")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule portas_sao_interfaces =
      classes().that().resideInAPackage(DOMAIN_PORT)
          .should().beInterfaces()
          .as("portas seguem o princípio 'programe para uma interface, não para uma implementação' (GoF/DIP)")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule implementacoes_de_porta_residem_em_infrastructure =
      classes().that().implement(resideInAPackage(DOMAIN_PORT))
          .should().resideInAPackage(INFRASTRUCTURE)
          .as("toda implementação concreta de uma porta é um adapter (padrão Adapter do GoF) e pertence à infraestrutura")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule casos_de_uso_ficam_no_pacote_correto =
      classes().that().haveSimpleNameEndingWith("UseCase")
          .should().resideInAPackage("..application.usecase..")
          .as("'UseCase' é a convenção do projeto para casos de uso da camada de aplicação")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule casos_de_uso_expoem_um_unico_ponto_de_entrada =
      methods().that().arePublic()
          .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("UseCase")
          .should().haveName("handle")
          .as("cada caso de uso tem uma única responsabilidade (SRP), expressa por um único método público 'handle'")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule excecoes_de_dominio_seguem_convencao =
      classes().that().resideInAPackage("..domain.exception..")
          .should().haveSimpleNameEndingWith("Exception")
          .andShould().beAssignableTo(RuntimeException.class)
          .as("exceções de domínio são unchecked e nomeadas de forma consistente")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule dtos_de_aplicacao_sao_imutaveis =
      fields().that().areDeclaredInClassesThat().resideInAPackage("..application.dto..")
          .should().beFinal()
          .as("commands e results trafegados entre camadas (padrão Command) não devem ser mutáveis")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule sem_ciclos_dentro_do_dominio =
      slices().matching("..notificationworker.domain.(*)..").should().beFreeOfCycles()
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule sem_ciclos_dentro_da_application =
      slices().matching("..notificationworker.application.(*)..").should().beFreeOfCycles()
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule sem_ciclos_dentro_da_infrastructure =
      slices().matching("..notificationworker.infrastructure.(*)..").should().beFreeOfCycles()
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule sem_injecao_por_campo =
      NO_CLASSES_SHOULD_USE_FIELD_INJECTION.allowEmptyShould(true);

  @ArchTest
  static final ArchRule sem_acesso_a_streams_padrao =
      NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.allowEmptyShould(true);

  @ArchTest
  static final ArchRule sem_excecoes_genericas =
      NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS.allowEmptyShould(true);

  @ArchTest
  static final ArchRule sem_java_util_logging =
      NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.allowEmptyShould(true);
}
