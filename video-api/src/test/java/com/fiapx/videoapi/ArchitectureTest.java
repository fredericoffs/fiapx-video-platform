package com.fiapx.videoapi;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.context.annotation.Configuration;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.fiapx.videoapi", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String DOMAIN = "..videoapi.domain..";
  private static final String APPLICATION = "..videoapi.application..";
  private static final String INFRASTRUCTURE = "..videoapi.infrastructure..";
  private static final String INFRA_CONFIG = "..videoapi.infrastructure.config..";
  private static final String DOMAIN_PORT = "..videoapi.domain.port..";

  @ArchTest
  static final ArchRule dominio_nao_depende_de_application =
      noClasses().that().resideInAPackage(DOMAIN)
          .should().dependOnClassesThat().resideInAPackage(APPLICATION)
          .as("o domínio é o núcleo da arquitetura hexagonal e não pode conhecer a camada de aplicação");

  @ArchTest
  static final ArchRule dominio_nao_depende_de_infrastructure =
      noClasses().that().resideInAPackage(DOMAIN)
          .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE)
          .as("o domínio não pode depender de adapters (regra da dependência: setas sempre apontam para dentro)");

  @ArchTest
  static final ArchRule dominio_e_livre_de_frameworks =
      noClasses().that().resideInAPackage(DOMAIN)
          .should().dependOnClassesThat().resideInAnyPackage(
              "org.springframework..", "jakarta.persistence..", "jakarta.validation..",
              "tools.jackson..", "com.fasterxml.jackson..", "software.amazon.awssdk..",
              "org.springframework.amqp..", "io.jsonwebtoken..")
          .as("o domínio deve ser Plain Java, sem acoplamento a frameworks (persistence/framework ignorance)");

  @ArchTest
  static final ArchRule application_nao_depende_de_adapters_de_infraestrutura =
      noClasses().that().resideInAPackage(APPLICATION)
          .should().dependOnClassesThat(resideInAPackage(INFRASTRUCTURE).and(not(resideInAPackage(INFRA_CONFIG))))
          .as("casos de uso dependem apenas de portas do domínio; infrastructure.config (records de "
              + "@ConfigurationProperties) é a única exceção tolerada, por não conter lógica de adapter");

  @ArchTest
  static final ArchRule portas_sao_interfaces =
      classes().that().resideInAPackage(DOMAIN_PORT)
          .should().beInterfaces()
          .as("portas seguem o princípio 'programe para uma interface, não para uma implementação' (GoF/DIP)");

  @ArchTest
  static final ArchRule implementacoes_de_porta_residem_em_infrastructure =
      classes().that().implement(resideInAPackage(DOMAIN_PORT))
          .should().resideInAPackage(INFRASTRUCTURE)
          .as("toda implementação concreta de uma porta é um adapter (padrão Adapter do GoF) e pertence à infraestrutura");

  @ArchTest
  static final ArchRule controllers_dependem_apenas_de_application =
      noClasses().that().resideInAPackage("..infrastructure.web..")
          .should().dependOnClassesThat().resideInAnyPackage(
              DOMAIN_PORT,
              "..infrastructure.persistence..",
              "..infrastructure.messaging..",
              "..infrastructure.security..",
              "..infrastructure.storage..")
          .as("controllers são o driving adapter e devem depender apenas de casos de uso, nunca de portas ou de outros adapters");

  @ArchTest
  static final ArchRule listeners_dependem_apenas_de_application =
      noClasses().that().resideInAPackage("..infrastructure.messaging..")
          .and().haveSimpleNameEndingWith("Listener")
          .should().dependOnClassesThat().resideInAnyPackage(
              "..infrastructure.persistence..", "..infrastructure.storage..")
          .as("listeners (padrão Observer) delegam a regra de negócio aos casos de uso, sem acessar adapters de persistência/armazenamento diretamente");

  @ArchTest
  static final ArchRule casos_de_uso_ficam_no_pacote_correto =
      classes().that().haveSimpleNameEndingWith("UseCase")
          .should().resideInAPackage("..application.usecase..")
          .as("'UseCase' é a convenção do projeto para casos de uso da camada de aplicação");

  @ArchTest
  static final ArchRule casos_de_uso_expoem_um_unico_ponto_de_entrada =
      methods().that().arePublic()
          .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("UseCase")
          .should().haveName("handle")
          .as("cada caso de uso tem uma única responsabilidade (SRP), expressa por um único método público 'handle'");

  @ArchTest
  static final ArchRule excecoes_de_dominio_seguem_convencao =
      classes().that().resideInAPackage("..domain.exception..")
          .should().haveSimpleNameEndingWith("Exception")
          .andShould().beAssignableTo(RuntimeException.class)
          .as("exceções de domínio são unchecked e nomeadas de forma consistente");

  @ArchTest
  static final ArchRule dtos_de_aplicacao_sao_imutaveis =
      fields().that().areDeclaredInClassesThat().resideInAPackage("..application.dto..")
          .should().beFinal()
          .as("commands e results trafegados entre camadas (padrão Command) não devem ser mutáveis");

  @ArchTest
  static final ArchRule sem_ciclos_dentro_do_dominio =
      slices().matching("..videoapi.domain.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule sem_ciclos_dentro_da_application =
      slices().matching("..videoapi.application.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule sem_ciclos_dentro_da_infrastructure =
      slices().matching("..videoapi.infrastructure.(*)..").should().beFreeOfCycles()
          // classes @Configuration são a composition root: é esperado que amarrem beans de
          // vários pacotes de adapter, então não contam como ciclo arquitetural
          .ignoreDependency(annotatedWith(Configuration.class), alwaysTrue());

  @ArchTest
  static final ArchRule sem_injecao_por_campo = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

  @ArchTest
  static final ArchRule sem_acesso_a_streams_padrao = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  @ArchTest
  static final ArchRule sem_excecoes_genericas = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

  @ArchTest
  static final ArchRule sem_java_util_logging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
