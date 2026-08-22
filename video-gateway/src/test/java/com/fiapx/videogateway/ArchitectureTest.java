package com.fiapx.videogateway;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

/**
 * Tratei video-gateway como uma camada de borda (Spring Cloud Gateway: roteamento, CORS, rate
 * limit) sem regra de negócio própria, por isso não há domain/application a isolar e não aplico
 * aqui as regras de camada hexagonal (ver video-api/video-worker) — só os princípios gerais
 * abaixo.
 */
@AnalyzeClasses(packages = "com.fiapx.videogateway", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule properties_de_configuracao_sao_imutaveis =
      fields().that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Properties")
          .should().beFinal()
          .as("classes de @ConfigurationProperties devem ser records/imutáveis");

  @ArchTest
  static final ArchRule sem_injecao_por_campo = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

  @ArchTest
  static final ArchRule sem_acesso_a_streams_padrao = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  @ArchTest
  static final ArchRule sem_excecoes_genericas = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

  @ArchTest
  static final ArchRule sem_java_util_logging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
