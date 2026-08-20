# **Learner Lab**

[Visão geral do ambiente](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#envOverview)</br>
[Navegação pelo ambiente](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#envNav)</br>
[Acessar o console de gerenciamento da AWS](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#mgmtConsole)</br>
[Restrição de Regiões](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#regionRest)</br>
[Uso do serviço e outras restrições](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#services)</br>
[Usar o terminal no navegador](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#terminal)</br>
[Executar os comandos da AWS CLI](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#clicommands)</br>
[Usar o AWS SDK for Python](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#sdk)</br>
[Preservar seu orçamento](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#budget)</br>
[Acessar instâncias do EC2](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#ec2)</br>
[Acesso por SSH a instâncias do EC2](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#ssh)</br>
[Acesso por SSH pelo Windows](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#sshwindows)</br>
[Acesso por SSH pelo Mac](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#sshmac)</br>

_Data da última atualização das instruções: 24/06/2025_

**Visão geral do ambiente**

Este Learner Lab oferece um ambiente de sandbox para a exploração específica de serviços da AWS.

**Este ambiente é de longa duração**. Quando o cronômetro da sessão for zerado, a sessão terminará, mas todos os dados e os recursos criados na conta da AWS serão retidos. Se iniciar uma nova sessão (por exemplo, no dia seguinte), você verá que seu trabalho ainda estará no ambiente de laboratório.

As instâncias do EC2 em execução serão interrompidas e, depois, serão reiniciadas automaticamente da próxima vez que você iniciar uma sessão. As instâncias do bloco de notas do SageMaker serão interrompidas, mas não serão reiniciadas da próxima vez que você iniciar uma sessão. As aplicações do SageMaker Canvas permanecerão em execução até que sejam excluídas.

**IMPORTANTE**: monitore seu orçamento de laboratório na interface de laboratório acima. Sempre que você tiver uma sessão de laboratório ativa, as informações mais recentes conhecidas do orçamento restante serão exibidas na parte superior desta tela. Esses dados são provenientes do AWS Budgets, que, geralmente, são atualizados a cada oito a doze horas. Dessa forma, _o orçamento restante observado pode não refletir a atividade mais recente de sua conta._ **Se você exceder o orçamento de laboratório, sua conta será desativada e todo o andamento e os recursos serão perdidos**. Portanto, é importante gerenciar seus gastos. Leia sobre [como preservar seu orçamento](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#budget).

**Navegação pelo ambiente**

Use o link **Leiame** acima para voltar a estas instruções a qualquer momento.

Use o link **Detalhes da AWS** acima para acessar informações sobre seu ambiente.

**Dica:** você pode redimensionar esse painel a qualquer momento arrastando a barra à esquerda destas instruções para estreitá-lo ou aumentá-lo.

Use o link **Redefinir** acima se quiser restaurar sua conta da AWS para as configurações que você usava antes de executar sessões deste ambiente de laboratório. Essa opção não redefinirá seu orçamento. **_CUIDADO_**: se optar por redefinir e, depois, selecionar Sim para confirmar que deseja redefinir, você vai _excluir permanentemente_ tudo o que criou ou armazenou na conta da AWS.

**Acessar o Console de Gerenciamento da AWS**

1. Na parte superior destas instruções, selecione  
     **Start Lab** (Iniciar laboratório) para iniciar a sessão de laboratório.

- A sessão de laboratório será iniciada, e as informações da sessão serão exibidas.
- Um cronômetro acima mostra o tempo restante da sessão.  
    **Dica:** você pode atualizar a duração da sessão a qualquer momento selecionando Start Lab (Iniciar laboratório) novamente antes que o cronômetro seja zerado.

3. Selecione o link Leiame para voltar para estas instruções.
4. Conecte-se ao Console de Gerenciamento da AWS clicando no link da **AWS** acima da janela do terminal.

- É necessário estar conectado ao Console de Gerenciamento da AWS.  
  **Dica**: se uma nova guia não for aberta, você verá um banner ou um ícone na parte superior do navegador com uma mensagem informando que o navegador está impedindo que o site abra janelas pop-up. Clique no banner ou no ícone e escolha **Permitir pop-ups**.  
    **Dica**: se você tiver interesse em interagir com a conta da AWS de forma programática, leia a seção [Usar o terminal no navegador](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#terminal) abaixo para obter detalhes.  
    

**Restrição de regiões**

O acesso ao serviço é limitado às regiões **us-east-1** e **us-west-2**, a menos que mencionado de outra forma nos detalhes do serviço abaixo. Se você carregar uma página de console do serviço em outra região da AWS, verá mensagens de erro de acesso.

**Uso do serviço e outras restrições**

Os seguintes serviços podem ser usados: Limitações específicas são aplicáveis, conforme documentado. Qualquer tentativa de exceder o limite de um serviço pode resultar na desativação imediata da conta da AWS e todos os recursos na conta podem ser imediatamente excluídos. As restrições de serviços estão sujeitas a alterações.

**Amazon API Gateway**

- Esse serviço pode assumir o perfil do IAM LabRole.

**AWS App Mesh**

**Application Auto Scaling**

- Esse serviço pode assumir o perfil do IAM LabRole.

**Amazon Athena**

- Esse serviço pode assumir o perfil do IAM LabRole.

**Amazon Aurora**

**AWS Backup**

**AWS Batch**

- Esse serviço pode assumir o perfil do IAM LabRole.

**AWS Certificate Manager (ACM)**

**AWS Cloud9**

- Esse serviço pode assumir o perfil do IAM LabRole.
- Tipos de instância compatíveis: nano, micro, small, medium, large e c4.xlarge. **Observação**: nem todos os tipos de instância são compatíveis com todas as AZs.
- **Dica**: ao criar uma instância do Cloud9 com o tipo de ambiente _Nova instância do EC2_, em _Configurações de rede_, escolha **Secure Shell (SSH)**.

**AWS CloudFormation**

- Esse serviço pode assumir o perfil do IAM LabRole.

**AWS CloudShell**

- Você poderá usar as sugestões do Amazon Q no CloudShell se mudar do shell padrão do Bash para o Z executando o comando zsh. Uma vez no prompt do shell Z, comece a digitar um comando para receber sugestões. Por exemplo, digite aws e aguarde. A sugestão pode ser, por exemplo, aws s3 cp s3://.... Para aceitar o preenchimento de código, use a tecla de seta para a direita no teclado. Você pode ver sugestões alternativas (caso haja alguma) antes de aceitar uma delas usando a tecla Tab.

**AWS CloudTrail**

- Esse serviço pode assumir o perfil do IAM LabRole.
- É possível criar um CloudTrail, mas você não poderá ativar o registro em log do CloudWatch para a trilha.

**Amazon CloudWatch**

**AWS CodeCommit**

- Esse serviço pode assumir o perfil do IAM LabRole.

**AWS CodeDeploy**

- Esse serviço pode assumir o perfil do IAM LabRole.

**AWS Config**

**Relatórios de custo e uso da AWS**

**AWS Cost Explorer**

**AWS DeepComposer**

**AWS DeepLens**

**AWS DeepRacer**

- Esse serviço pode assumir o perfil do IAM LabRole.

**AWS Directory Service**

**Amazon DynamoDB**

- Esse serviço pode assumir o perfil do IAM LabRole.

**Amazon EC2 Auto Scaling**

- Esse serviço pode assumir o perfil do IAM LabRole.
- Tipos de instância compatíveis: nano, micro, small, medium e large.
- Leia as especificações _Limites de instâncias em execução simultânea_ documentadas nos detalhes do serviço EC2 abaixo para tomar conhecimento de restrições adicionais.
- _Recomendação_: defina sua necessidade real para evitar o uso acima de seu orçamento de custo.

**AWS Elastic Beanstalk**

- Esse serviço pode assumir o perfil do IAM LabRole.
- _Para criar uma aplicação_: selecione **Criar aplicativo**, forneça um nome à aplicação, selecione uma plataforma e, depois, selecione **Configurar mais opções**. Role para baixo até acessar o painel Segurança e selecione **Editar**. Em _Perfil de serviço_, selecione **LabRole**. Se o ambiente estiver na região AWS us-east-1, para o _par de chaves do EC2,_ selecione **vockey** e, para _Perfil de instância do IAM_, selecione **LabInstanceProfile**. Clique em **Salvar** e, depois, **Criar aplicativo**.
- Tipos de instância compatíveis: nano, micro, small, medium e large. Se você tentar iniciar um tipo de instância maior, ela será terminada.

**Amazon Elastic Block Store (EBS)**

- O tamanho máximo do volume é de 100 GB
- Não há suporte para PIOPs

**Amazon Elastic Compute Cloud (EC2)**

- Esse serviço pode assumir o perfil do IAM LabRole.
- AMIs compatíveis:

- AMI disponível em us-east-1 ou us-west-2. Por exemplo, Quick Start AMIs, My AMIs e Community AMIs.
- AMIs do AWS Marketplace não são compatíveis. AMIs, como MacOS, que precisam ser iniciadas como uma instância dedicada ou em um host dedicado também não são compatíveis.
- Recomendação: para iniciar uma instância com um sistema operacional convidado do **_Microsoft Windows_**, do **_Amazon Linux_** ou de uma das várias outras distribuições conhecidas do Linux, selecione "Executar instâncias" e, depois, escolha entre as disponíveis na guia "Quick Start".

- Tipos de instância compatíveis: nano, micro, small, medium e large.
- Somente instâncias sob demanda
- _Limites de instâncias em execução simultânea_ de acordo com a região compatível:

- Máximo de nove instâncias do EC2 em execução simultânea, independentemente do tamanho da instância. Se você tentar iniciar mais, as instâncias excedentes serão terminadas (e nove permanecerão em execução).  
                                                                                     Observação: serviços, como EMR e Elastic Beanstalk, também podem iniciar instâncias do EC2. As nove instâncias do EC2 em execução simultânea se aplicam a todos os serviços que criam instâncias visíveis no console do EC2.
- Máximo de 32 vCPUs usadas por instâncias em execução simultânea, independentemente do tamanho ou do número de instâncias. Por exemplo, instâncias t2.micro usam uma vCPU cada, então, é possível executar até 32 em us-west-2 (mas apenas nove em us-east-1 devido à outra limitação listada acima)  
  Observação: o limite máximo de 32 vCPUs se aplica a todos os serviços que criam instâncias visíveis no console do EC2.
- **Cuidado**: qualquer tentativa de ter 20 ou mais instâncias em execução simultânea (seja qual for o tamanho) causará a desativação imediata da conta da AWS e todos os recursos na conta serão excluídos imediatamente.
- _Recomendação_: defina sua necessidade real para evitar o uso acima de seu orçamento de custo.

- Volumes do EBS: tamanhos de até 100 GB e o tipo deve ser SSD de uso geral (gp2, gp3), HDD frio (sc1) ou padrão.
- Pares de chaves: se você estiver criando uma instância do EC2 em qualquer Região AWS que não seja a us-east-1, o par de chaves vockey não estará disponível. Nesses casos, você precisa criar um par de chaves e baixá-lo ao criar a instância do EC2. Depois, use o novo par de chaves para se conectar a essa instância.
- O atributo EC2 Fleet não é compatível com os Learner Labs.
- Uma função chamada **LabRole** e um perfil de instância chamado **LabInstanceProfile** foram pré-criados para você. Você poderá anexar a função (por meio do perfil de instância) a uma instância do EC2 quando desejar acessar uma instância do EC2 (terminal no navegador) usando o Gerenciador de sessões do AWS Systems Manager. A função também concede permissões a todas as aplicações em execução na instância para acessar muitos outros serviços da AWS por meio da instância.
- **Dicas**:

- Quando sua sessão terminar, o ambiente de laboratório _poderá_ colocar as instâncias em execução no estado "interrompido".  
    Se você ativou a opção "Proteção contra interrupção" em qualquer instância, essa proteção será removida quando a sessão terminar e a instância ainda será interrompida. A proteção contra interrupção _não_ será reativada automaticamente quando você iniciar uma nova sessão posteriormente.
- Ao iniciar uma nova sessão, o ambiente de laboratório iniciará todas as instâncias que foram interrompidas anteriormente por você ou pelo ambiente de laboratório quando a sessão foi encerrada.
- As instâncias que foram interrompidas e reiniciadas receberão um novo endereço IP público IPv4, a menos que você tenha um endereço IP elástico associado à instância.

- **Recomendações**:

- Para preservar seu orçamento de laboratório, interrompa todas as instâncias do EC2 em execução antes de parar de usar a conta no dia (e termine-as se não forem mais necessárias).
- Esteja ciente de todas as instâncias mantidas na conta entre as sessões porque elas serão executadas (e lançadas em seu orçamento) quando você iniciar o laboratório novamente, a menos que você se lembre de interrompê-las manualmente depois de iniciar o laboratório.

**Amazon Elastic Container Registry (ECR)**

- O perfil do IAM LabRole tem acesso somente leitura a esse serviço e, como usuário do console, você tem acesso de gravação ao serviço.

**Amazon Elastic Container Service (ECS)**

- Tipos de instância compatíveis: nano, micro, small, medium e large.
- Dicas:

- **Ao criar um cluster**: para evitar erros de permissão (não é possível criar uma função), no caso de tentativa de criar um cluster que usa instâncias do EC2 para a infraestrutura (em vez do Fargate), crie um grupo do Auto Scaling usando o console do EC2 e opte por usar o grupo do Auto Scaling existente ao criar o cluster do ECS.

- Se for exibida uma mensagem informando que “_não foi possível assumir a função vinculada ao serviço ECS_”, escolha o botão para voltar e tente novamente. Às vezes, isso acontece se a função vinculada ao serviço ainda não existir em sua conta.

- **Ao criar uma definição de tarefa**: para evitar erros de permissão, defina _LabRole_ como função da tarefa e função de execução da tarefa.

**Amazon Elastic File System (EFS)**

- Esse serviço pode assumir o perfil do IAM LabRole.

**Amazon Elastic Kubernetes Service (EKS)**

- Esse serviço pode assumir os perfis do IAM com o identificador _LabEksClusterRole_ criado para _Cluster_ e _Node_.
- Tipos de instância compatíveis: nano, micro, small, medium e large.

**Elastic Load Balancing (ELB)**

- Esse serviço pode assumir o perfil do IAM LabRole.

**Amazon Elastic MapReduce (EMR)**

- Esse serviço pode assumir o perfil do IAM LabRole.
- Tipos de instância compatíveis: nano, micro, small, medium e large. Se você tentar iniciar um tipo de instância maior, ela será terminada.
- **Dica**: se você tiver problemas para iniciar um cluster com êxito, tente usar o tipo de instância m4.large. _Exemplo_ Detalhes de configuração do cluster do EMR:

- _Configuração do cluster_: grupos de instâncias, Primário: **m4.large**, Núcleo: **m4.large**, Tarefa -1: **m4.large**
- _Configuração de provisionamento_: Núcleo: 1 instância e Tarefa -1: 1 instância
- _Configuração de segurança_: par de chaves do EC2: **vockey**
- _Perfis do IAM_: _perfil de serviço do Amazon EMR_: escolher existente: **EMR_DefaultRole**, _Perfil de instância do EC2 para o Amazon EMR_: escolher existente: **EMR_EC2_DefaultRole**

- **Observação:** se você encontrar o problema com a criação do bucket do S3 como parte da criação do cluster:

- Expanda a seção: **Logs de cluster** e **desmarque** o valor de _Publish cluster-specific logs to Amazon S3_ (Publicar logs específicos do cluster no Amazon S3) e continue com a criação do cluster. Os logs não serão armazenados no bucket do S3, mas o cluster será criado.

- Máximo de 32 vCPUs usadas por instâncias do EC2 de execução simultânea em uma Região AWS. Observe que você também está limitado a iniciar até nove instâncias (de qualquer tamanho) em uma região simultaneamente.  
  **Observação**: _um cluster do EMR não continuará a funcionar se a sessão for encerrada_. Nos Learner Labs, o encerramento da sessão faz com que as instâncias do EC2 que o cluster do EMR usa sejam interrompidas, e não há suporte à interrupção de um cluster do EMR (pela AWS). _Recomendação_: grave resultados de trabalhos do EMR no S3 se você precisar preservá-los antes de encerrar a sessão atual do Learner Lab e, depois, leia-os de volta em um novo cluster do EMR conforme necessário quando você iniciar a próxima sessão do Learner Lab.

**Amazon ElastiCache**

**Amazon EventBridge**

- Esse serviço pode assumir o perfil do IAM LabRole.
- Use LabRole se estiver criando um pipe do EventBridge.

**AWS Fargate**

- Esse serviço pode assumir o perfil do IAM LabRole.

**AWS Glue**

- Esse serviço pode assumir o perfil do IAM LabRole.  
  **Observação:** as seguintes limitações se aplicam à configuração de trabalho de ETL do AWS Glue

- Tipo de operador permitido: G.1X, Padrão
- Número máximo de operadores: 10
- Simultaneidade máxima: 1

**AWS Glue DataBrew**

- Esse serviço pode assumir o perfil do IAM LabRole.

**Amazon GuardDuty**

**AWS Health**

**AWS Identity and Access Management (AWS IAM)**

- Acesso extremamente limitado. Você não pode criar usuários ou grupos. Você não pode criar funções, com exceção de funções vinculadas a serviços.
- Em geral, a criação de funções de serviço é permitida. Se o serviço precisar criar uma função para você, talvez seja necessário tentar criar novamente a função se ocorrer uma falha na primeira vez.
- Uma função chamada **LabRole** já foi criada para você. Essa função foi projetada para ser usada quando você deseja anexar uma função a um recurso em um serviço da AWS. Ela concede a muitos serviços da AWS acesso a outros serviços da AWS e tem permissões muito semelhantes às que você tem como usuário no console. 

- Exemplo de uso: anexe o LabRole por meio do perfil de instância chamado **LabInstanceProfile** a uma instância do EC2 para terminal no acesso do navegador a um sistema operacional convidado da instância do EC2 usando o Gerenciador de sessões do AWS Systems Manager.
- Outro exemplo: anexe o LabRole a uma função do Lambda para que ela possa acessar o S3, o CloudWatch, o RDS ou outro serviço.
- Outro exemplo: anexe o LabRole a uma instância do bloco de notas do SageMaker para que a instância possa acessar arquivos em um bucket do S3.

**Amazon Inspector**

**AWS IoT 1-Click**

**AWS IoT Core**

- Esse serviço pode assumir o perfil do IAM LabRole.

**AWS IoT GreenGrass**

**AWS Key Management Service (KMS)**

- Esse serviço pode assumir o perfil do IAM LabRole.

**Amazon Kinesis**

- Se você tentar criar um _Bloco de anotações do Kinesis Data Analytics Studio_, escolha “Criar com configurações personalizadas” e **LabRole** na área de configurações do IAM.
- Se você tentar criar um _stream de entrega do Kinesis_, selecione “Configurações avançadas” e use a **LabRole** existente.

**AWS Lambda**

- **Dica**: anexe a **LabRole** existente a qualquer função que você criar, se esta função precisar de permissões para interagir com outros serviços da AWS.
- São permitidas no máximo 10 instâncias do ambiente de execução do Lambda em execução simultânea. Consulte a [documentação](https://docs.aws.amazon.com/lambda/latest/dg/lambda-concurrency.html) para entender como a simultaneidade é calculada.

**Amazon Machine Learning (Amazon ML)**

**Assinaturas do AWS Marketplace**

- Acesso somente leitura extremamente limitado.

**AWS Mobile Hub**

**AWS OpsWorks**

**Amazon Q Developer**

- O Amazon Q Developer oferece muitos recursos, incluindo sugestões de código em linha que podem ser usadas em uma variedade de editores de código, como o editor de código do AWS Lambda. Para ver uma lista de IDEs compatíveis com a geração de sugestões em linha, consulte a [documentação da AWS](https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/setting-up-AWS-coding-env.html).  
  **Observação**: alguns IDEs se referem ao recurso de sugestão automática com o nome de CodeWhisperer. Os recursos do CodeWhisperer estão sendo migrados para o Amazon Q Developer.

**Amazon Redshift**

- Esse serviço pode assumir o perfil do IAM LabRole.
- Tipo de instância compatível: ra3.large
- Tamanho de cluster compatível: máximo de duas instâncias

**Amazon Rekognition**

- Esse serviço pode assumir o perfil do IAM LabRole.  
  **Observação:** o uso do serviço está sujeito aos limites a seguir, que são suficientes para explorar os respectivos recursos ou criar exemplos de aplicações para a experiência de aprendizado.

- Limite de detecções faciais do Rekognition: 1.000
- Limite de detecções de rótulos do Rekognition: 1.000                             
- Máximo de unidades de inferência do Rekognition: 1
- Unidade de inferência InService do Rekognition: 1

**Amazon Relational Database Service (RDS)**

- Esse serviço pode assumir o perfil do IAM LabRole.
- Mecanismos de banco de dados compatíveis: Amazon Aurora (provisionado), Oracle, Microsoft SQL, MySQL, PostgreSQL e MariaDB. Observação: se você estiver criando uma instância do RDS usando um modelo do CloudFormation, certifique-se de especificar o tipo de mecanismo usando letras minúsculas.
- Tipos de instância compatíveis: nano, micro, pequena e média (dica: selecione _Burstable classes_ [Classes com capacidade de intermitência] para encontrá-las).
- Tipos de armazenamento compatíveis: volumes do EBS, tamanhos de até 100 GB e tipo SSD de uso geral (gp2). Tipos de armazenamento PIOPS não são compatíveis.
- Somente tipos de classe de instância de banco de dados sob demanda.
- **_Enhanced monitoring is not supported_** (O monitoramento aprimorado não é compatível) (_desmarque_ esta configuração padrão no painel _Configuração adicional/monitoramento_).
- **Dica**: para preservar seu orçamento de laboratório, interrompa todas as instâncias do RDS em execução antes de parar de usar a conta no dia (ou termine-as se não forem mais necessárias).
- **Cuidado**: quando uma sessão de laboratório termina, talvez o ambiente de laboratório não interrompa uma instância do RDS ou um cluster que você deixar em execução. Além disso, mesmo se você _interromper_ uma instância do RDS, se ela permanecer parada por sete dias, _a AWS a reiniciará automaticamente_, o que aumentará o impacto nos custos.

**AWS Resource Groups e Tag Editor**

- Esse serviço pode assumir o perfil do IAM LabRole.

**Amazon Route 53**

- Você não pode registrar um domínio.

**Amazon SageMaker**

- Esse serviço pode assumir o perfil do IAM LabRole.
- Tipos de instância do SageMaker compatíveis: somente ml.t3.medium, ml.t3.large, ml.t3.xlarge, ml.m5.large, ml.m5.xlarge, ml.c5.large, ml.c5.xlarge.
- Máximo de bloco de anotações do Sagemaker: dois
- Máximo de aplicações do Sagemaker: duas
- Para **criar** um **domínio** do **SageMaker**:

- Clique em **Domínios** e em **Criar domínio**.
- Clique em **Configurar para organizações** e em **Configurar**.

- Etapa 1: configurar detalhes e usuários do domínio

- Dê ao domínio um nome, como myDomain.
- Mantenha a opção **Fazer login por meio do IAM** marcada como padrão.
- Deixe _Who will use Sagemaker_ (Quem usará o Sagemaker) em branco.
- Selecione **Próximo**.

- Etapa 2: configurar perfis e atividades de ML

- Clique em **Usar um perfil existente**.
- Defina _Função de execução padrão_ como **LabRole**.
- Selecione **Próximo**.

- Etapa 3: configurar aplicações  
  Observação: ignore e feche qualquer mensagem sobre problemas de permissões servicequotas:RequestServiceQuotaIncrease.

- No painel do _StageMaker Studio_, clique em **SageMaker Studio – Novo**.
- No painel do _JupyterLab_:

- Clique em **Habilitar desligamento por inatividade** e defina-o como 60 minutos.
- Clique em **Permitir que os usuários definam um tempo de desligamento inativo personalizado** e defina o máximo como 600.

- No painel do _Canvas_:

- Clique em **Configurar o Canvas**.
- Role para baixo até **Configuração de modelos prontos para uso do Canvas** e clique em **Usar um perfil de execução existente**.
- Em **Nome do perfil de execução**, clique em **Inserir um ARN personalizado da função do IAM**.
- No campo do ARN personalizado do perfil do IAM, cole o ARN do LabRole. Ele será exibido no formato arn:aws:iam::ACCOUNT_ID:role/LabRole, em que ACCOUNT_ID será o ID real da sua conta.

- No painel _CodeEditor_:

- Clique em **Habilitar desligamento por inatividade** e defina-o como 60 minutos.
- Clique em **Permitir que os usuários definam um tempo de desligamento inativo personalizado** e defina o máximo como 600.

- Etapa 4: personalizar a interface do usuário do Studio – opcional

- Role até o final e clique em **Próximo**.

- Etapa 5: definir as configurações de rede

- Selecione somente VPC ou _Acesso público à internet_.
- Exemplo:

- Selecione **VPC Only** (Somente VPC).
- Em VPC for Studio to use (VPC a ser usada pelo Studio), selecione a VPC padrão.
- Clique no link **Console da VPC**. O console da VPC será aberto em uma nova guia. Na guia _Resource map_ (Mapa de recursos), anote os subconjuntos que são públicos. As sub-redes públicas são as conectadas a uma tabela de rotas que é roteada para uma conexão de rede "igw-..." (gateway da internet).
- De volta ao console do SageMaker, escolha pelo menos duas das sub-redes públicas identificadas.
- Selecione o grupo de segurança padrão.
- Selecione **Próximo**.

- Etapa 6: configurar o armazenamento

- Role até o final e clique em **Próximo**.

- Etapa 7: revisar e criar

- Role até a parte inferior e clique em **Enviar**.

- Aguarde a criação do domínio. Geralmente, leva-se de cinco a oito minutos para a conclusão. Atualize a guia do navegador ocasionalmente para saber quando ela tiver sido concluída.

- Para criar um **perfil de usuário** do SageMaker:

- Na lista de domínios do SageMaker, clique no link do nome do domínio criado.
- Na guia Perfis de usuários, selecione **Adicionar usuário**.
- Em _Configurações gerais_, para _Perfil de execução_, selecione **LabRole** e clique em **Próximo**.
- Na _Etapa 2_, clique em **Próximo**.
- Na _Etapa 3_, clique em **Próximo**.
- Na _Etapa 4_: mantenha a opção _Herdar configurações do domínio_ selecionada e clique em **Próximo**.
- Na Etapa 5, role até a parte inferior e clique em **Enviar**.

- Para criar um espaço de editor de código, você pode usar o **Visual Studio Code Open Source**:

- Clique em **Studio**.
- No painel _Conceitos básicos_, verifique se o perfil de usuário criado está selecionado e clique em **Abrir o Studio**.
- Clique em **Skip Tour** (Pular tour) por enquanto e, no painel **Aplicações**, selecione **Editor de código**.
- Clique no botão **Create Code Editor space** (Criar espaço do editor de código) no canto superior direito.
- Dê a ele um nome como mySpace e clique em **Criar espaço**.
- Verifique as configurações de espaço antes de executá-lo. Os padrões, como o tipo de instância ml.t3.medium padrão, são compatíveis.
- Clique em **Executar espaço**.
- Depois que o espaço for iniciado, clique em Open Code Editor (Abrir editor de código).  
                                  Uma nova guia do navegador é aberta, exibindo um IDE. Consulte mais informações sobre o editor de código do SageMaker Studio na [documentação da AWS](https://docs.aws.amazon.com/sagemaker/latest/dg/code-editor.html).

- Há compatibilidade limitada para atributos do **SageMaker Studio**. Alguns projetos JumpStart do SageMaker exigem mais permissões de acesso do que podemos conceder nos Learner Labs.

- Para usar o SageMaker Studio, crie primeiro um domínio e um perfil de usuário do SageMaker (etapas documentadas acima).
- Quando você tiver um domínio e perfil de usuário, clique em **Studio**. Na lista **Aplicações**, no canto superior esquerdo, selecione uma aplicação.

- _Por exemplo_, selecione **JupyterLab** e **Create JupyterLab space** (Criar espaço do JupyterLab). Dê um nome ao espaço e selecione **Criar espaço**. Em seguida, escolha **Executar espaço**. Aguarde o espaço do JupyterLab iniciar e escolha **Abrir o JupyterLab**. Na interface de usuário do JupyterLab, você pode iniciar um bloco de notas, console ou outro tipo de recurso.

- Se você tiver um _espaço do Studio_ em execução, aparecerá como uma _Aplicação_ em execução no console do SageMaker Studio.

- Há compatibilidade limitada para atributos do **SageMaker Canvas**. Há muitos modelos de SageMaker Canvas (incluindo vários prontos para uso) que não são compatíveis nos Learner Labs. Por exemplo, se um modelo tiver "tecnologia" de um serviço da AWS que não seja compatível com os Learner Labs, o modelo não será executado nos Learner Labs.

- Para usar o SageMaker Canvas, crie primeiro um domínio e um perfil de usuário do SageMaker (veja as etapas acima).
- Quando você tiver um domínio e um perfil de usuário, na lista _Perfis de usuário_ na linha com o perfil que você deseja usar, clique em **Iniciar** > **Canvas**. O console do SageMaker Canvas é exibido.

- Você pode criar **instâncias do bloco de anotações do SageMaker**.
- **Dicas para preservar seu orçamento**:

- Clique no link **Painel do SageMaker** para ver a atividade recente, incluindo trabalhos, modelos ou instâncias em execução. Interrompa ou exclua tudo que estiver em execução e que não seja mais necessário.
- Quando a sessão for encerrada, o ambiente de laboratório _poderá_ colocar todas as instâncias do bloco de anotações do SageMaker em execução no estado "interrompido". As instâncias interrompidas do bloco de anotações do SageMaker _não_ serão reiniciadas automaticamente quando você iniciar uma nova sessão.
- Ao usar o SageMaker Canvas ou o SageMaker Studio, faça logout da **sessão** quando terminar de trabalhar nela. _Considere excluir as aplicações do SageMaker Canvas e do SageMaker Studio que não forem mais necessárias_.

**AWS Secrets Manager**

- Esse serviço pode assumir o perfil do IAM LabRole.

**AWS Security Hub**

**AWS Security Token Service (STS)**

**AWS Serverless Application Repository (SAR)**

**AWS Service Catalog**

- Esse serviço pode assumir o perfil do IAM LabRole.

**Amazon Simple Notification Service (SNS)**

- Esse serviço pode assumir o perfil do IAM LabRole.

**Amazon Simple Queue Service (SQS)**

- Esse serviço pode assumir o perfil do IAM LabRole.

**Amazon Simple Storage Service (S3)**

- Esse serviço pode assumir o perfil do IAM LabRole.

**Amazon Simple Storage Service Glacier (S3 Glacier)**

- Você não pode criar um Vault Lock

**Amazon Simple Workflow Service (SWF)**

**AWS Step Functions**

**AWS Systems Manager (SSM)**

- Uma função chamada **LabRole** e um perfil de instância chamado **LabInstanceProfile** foram pré-criados para você. Você poderá anexar a função (por meio do perfil de instância) a uma instância do EC2 quando desejar acessar uma instância do EC2 (terminal no navegador) usando o Gerenciador de sessões do AWS Systems Manager.

**Amazon Textract**

**AWS Trusted Advisor**

**Amazon Virtual Private Cloud (Amazon VPC)**

**AWS WAF: Web Application Firewall**

**AWS Well-Architected Tool**

**Acessar um terminal no navegador**

**AWS CloudShell** é um serviço da AWS que fornece um terminal no navegador. Você pode acessá-lo no Console de Gerenciamento da AWS, no topo da tela, selecionando o ícone do AWS CloudShell (destacado em vermelho na captura de tela abaixo).



Outras maneiras de acessar um terminal no navegador:

- Inicie uma **instância do EC2** à qual você anexou o **LabInstanceProfile** (que anexa o perfil do IAM LabRole) e conecte ao terminal nela usando o EC2 Instance Connect.

Dicas adicionais:

- As instâncias do AWS CloudShell e as instâncias do EC2 do tipo Amazon Linux 2 têm o cliente da AWS CLI já instalado.
- O AWS CloudShell têm credenciais da conta da AWS pré-configuradas no ambiente.

**Executar os comandos da AWS CLI**

Veja um exemplo de comando da AWS CLI para tentar executar em um terminal. Se você tiver criado instâncias do EC2 na região da conta padrão, executar esse comando fornecerá informações sobre elas:

aws ec2 describe-instances



Consulte a documentação [AWS CLI Command Reference](https://docs.aws.amazon.com/cli/latest/reference/) para saber mais sobre como usar a AWS CLI.

**Usar o AWS SDK for Python**

O AWS CloudShell também tem o Python 3 instalado com a biblioteca boto3 disponível. Você pode usá-lo para executar o código do AWS SDK para Python. Por exemplo:

$ python3

>>> import boto3

>>> ec2 = boto3.client('ec2', region_name='us-east-1') 

>>> ec2.describe_regions()

>>> exit()

$



Consulte a [documentação](https://boto3.amazonaws.com/v1/documentation/api/latest/index.html) para saber mais sobre como usar o AWS SDK for Python.

**Preservar seu orçamento**

Lembre-se, _se você exceder seu orçamento de laboratório, sua conta será desativada e todo o andamento e os recursos serão perdidos_. Detalhes sobre como monitorar seu orçamento são fornecidos [acima](https://labs.vocareum.com/web/4789711/5275926.0/ASNLIB/public/docs/lang/pt-br/README.html#monitor).

_Sugestões para evitar gastos excessivos_:

- Inicie somente o número de instâncias necessárias, dimensionadas de acordo com seus requisitos.
- Em geral, são os recursos de computação que você deixa em execução que consomem seu orçamento com mais rapidez. Desative-os quando não forem mais necessários ou, melhor ainda, exclua-os.
- Exemplos de recurso de computação:

- EC2, RDS, gateway NAT
- Instâncias do bloco de anotações do SageMaker e aplicações do SageMaker Canvas.
- Clusters do EMR, ECS ou EKS
- Aplicações do Elastic Beanstalk

- Use a [Calculadora de Preços da AWS](https://calculator.aws/) para estimar os custos.

- Por exemplo, a estimativa mostrada na captura de tela abaixo calculou o custo de executar os seguintes recursos por um mês:

- Uma _instância do EC2_ t3.medium do Linux em execução por seis horas por dia por um mês na Região us-east-1.
- Um _banco de dados RDS_ MySQL db.t2.small com 30GB de armazenamento, em uma AZ única, em execução por um mês na Região us-east-1.
- Um _gateway NAT_ em execução, processando 1GB por mês na Região us-east-1

-
_Os preços estão sujeitos a alterações. O cálculo acima é somente um exemplo de um momento no passado._

_Sugestões adicionais para reduzir o custo_:

- Descubra quais recursos existem em sua conta usando o atributo **Tag Editor**.  
  **Observação**: esta ferramenta não encontra _todos_ os tipos de recurso, mas pode localizar muitos tipos.

- Abra o console do _Resource Groups & Tag Editor_ e selecione **Tag Editor**.
- Em _Regiões_, selecione **us-east-1** e **us-west-2** e, em _Tipos de recurso_, selecione **Todos os tipos de recursos compatíveis**. Por fim, selecione **Pesquisar recursos**.
- Aguarde a conclusão da pesquisa. Você verá um grande número de avisos na parte superior da tela, indicando que não tem permissões para visualizar determinados recursos. Ignore esses avisos.
- Role a tela para baixo até o painel _Resultados da pesquisa de recursos_ para ver os recursos encontrados.

- Alguns dos recursos já existiam na sua conta quando você iniciou o laboratório e eles não consumirão uma quantidade significativa de seu orçamento. Eles incluem recursos do IAM, duas funções do Lambda, vários grupos de segurança e outros recursos relacionados à VPC.
- No entanto, você pode perceber outros recursos nos resultados da pesquisa criados por você e que, talvez, não sejam mais necessários.

- Crie suas soluções usando **modelos do CloudFormation**.

- Você pode usar o serviço para criar uma pilha que crie vários recursos nos serviços da AWS. Depois, quando você não precisar mais dos recursos, exclua a pilha (o que excluirá todos os recursos que ela criou). Você sempre pode usar o mesmo modelo para criar uma pilha para que ela recrie os recursos durante sua próxima sessão.

- Acesse o **AWS Trusted Advisor** e examine os resultados de otimização de custos. O serviço pode ajudar a identificar instâncias do EC2 com baixas taxas de utilização, instâncias ociosas do RDS ou Classic Load Balancers, volumes do EBS subutilizados e outras condições que podem ajudar você a economizar o valor restante em seu orçamento de laboratório.

**Acessar instâncias do EC2**

Ao iniciar instâncias do EC2 na Região padrão us-east-1 neste ambiente, selecione a opção para usar o par de chaves existente denominado **_vockey_** no momento da inicialização. Então:

- Acesse o link **Detalhes da AWS** acima destas instruções.

- Se você estiver usando um desktop ou um laptop Windows, selecione o botão **Baixar PPK** e salve o arquivo **labsuser.ppk**. Você pode usar esse arquivo para se conectar por SSH a uma instância do Linux EC2 ou do Windows EC2, geralmente usando uma ferramenta, como PuTTY.
- Se você estiver usando um desktop ou um laptop MacOS, selecione o botão **Baixar PEM** e salve o arquivo **labsuser.pem**. Você pode usar esse arquivo para se conectar por SSH a uma instância do Linux EC2 ou do Windows EC2, geralmente usando uma janela de terminal.

- **Para conectar-se via área de trabalho remota a uma instância do Windows EC2**:

- No console do EC2, selecione **Instâncias** e a instância à qual você deseja se conectar
- No menu **Ações**, selecione **Obter senha do Windows**
- Ao lado de _Caminho do par de chaves_, selecione **Procurar**.
- Procure e selecione o arquivo labsuser.pem que você baixou antes.
- Selecione **Descriptografar senha**.
- As informações de conexão são exibidas, como o DNS público da instância, o nome do usuário administrador e a senha descriptografada.
- Use um cliente do Remote Desktop Protocol (RDP) para conectar-se ao desktop da instância do EC2 usando esses detalhes de conexão.
- **Para se conectar usando SSH a uma instância do Linux, consulte a próxima seção**.

**Acesso por SSH a uma instância do EC2 que você iniciar**

As etapas abaixo descrevem como usar a chave SSH para se conectar à sua instância.

**Dica**: supondo-se que você tenha iniciado a instância com o par de chaves vockey e aberto a porta TCP 22 no grupo de segurança da instância, você também pode se conectar via SSH a uma instância do EC2 usando o terminal ao lado destas instruções. O terminal já tem o par de chaves disponível. Basta inserir o comando ssh -i ~/.ssh/labsuser.pem ec2-user@<public-ip> em que <public-ip> é o endereço público IPv4 real da instância.

**Usuários do Windows: usar SSH para conexão**

Estas instruções são apenas para usuários do Windows.

1. Baixe o software necessário.

- Você usará o **PuTTY** para se conectar às instâncias do Amazon EC2 com SSH. Se você não tiver o PuTTY instalado no computador, [baixe-o aqui](https://the.earth.li/~sgtatham/putty/latest/w64/putty.exe).

3. Abra o **putty.exe**
4. Configure o PuTTY para não atingir o tempo limite:

- Selecione **Conexão**.
- Defina **Segundos entre os keepalives** como 30

6. Isso permite manter a sessão do PuTTY aberta por mais tempo.
7. Configure a sua sessão do PuTTY:

- Selecione **Sessão**.
- **Nome do host (ou endereço IP)**: copie e cole o **Endereço IP público IPv4** para a instância. Para encontrá-lo, retorne ao console do EC2 e selecione **Instâncias**. Marque a caixa ao lado da instância e, na guia _Descrição_, copie o valor de **IP público IPv4**.
- De volta ao PuTTy, na lista **Conexão**, expanda **SSH**.
- Selecione **Autenticação** (sem expandir).
- Selecione **Procurar**.
- Procure e selecione o arquivo .ppk que você baixou.
- Selecione **Abrir** para selecioná-lo.
- Selecione **Abrir**.

9. Selecione **Sim** para confiar no host e conectar-se a ele.
10. Quando solicitado a fazer **login como**, insira: ec2-user  
    Isso conectará você à instância do EC2.

**Usuários do macOS e Linux : usar o SSH para se conectar**

Estas instruções são somente para usuários do Mac/Linux.

1. Leia os dois tópicos desta etapa antes de iniciar as ações, uma vez que não será possível visualizar estas instruções quando o painel Detalhes da AWS estiver aberto.

- Acesse o link **Detalhes da AWS** acima destas instruções.
- Selecione o botão **Baixar PEM** e salve o arquivo **labsuser.pem**.  
  Normalmente, seu navegador o salva no diretório Downloads.

3. Abra uma janela do terminal e altere o diretório cd para o diretório no qual o arquivo .pem foi baixado.  
   Por exemplo, execute este comando se ele tiver sido salvo no diretório Downloads:

   cd ~/Downloads

4. Altere as permissões na chave para serem somente leitura, executando este comando:

   chmod 400 labsuser.pem

5. Retorne ao Console de Gerenciamento da AWS e, no serviço do EC2, selecione **Instâncias**.  
   Marque a caixa ao lado da instância à qual você deseja se conectar.
6. Na guia _Descrição_, copie o valor de **IP público IPv4**.
7. Retorne à janela do terminal e execute este comando (substitua **<public-ip>** pelo endereço IP público real que você copiou):

   ssh -i <filename>.pem ec2-user@<public-ip>

8. Digite yes quando solicitado para permitir a primeira conexão ao servidor SSH remoto.  
   Como você está usando um par de chaves para autenticação, não será necessário fornecer uma senha.  
    

© 2025 Amazon Web Services, Inc. e suas afiliadas. Todos os direitos reservados. Este trabalho não pode ser reproduzido ou redistribuído, no todo ou em parte, sem a permissão prévia por escrito da Amazon Web Services, Inc. É proibido copiar, emprestar ou vender para fins comerciais.