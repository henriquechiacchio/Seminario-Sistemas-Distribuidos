# Consistência Contínua – Desvio de Idade

**Sistemas Distribuídos – Seminário #01 – Tema 13**
Dupla: Henrique e João Vitor

Sistema Cliente / Servidor / Réplica em Java, usando sockets UDP, que demonstra **consistência contínua em termos de desvio de idade**.

O **Servidor** mantém um arquivo `.txt` com temperaturas de cidades do sudoeste da Bahia. A **Réplica** guarda uma cópia desse arquivo, mas só é atualizada a cada **x segundos**. Assim, o **Cliente** pode baixar:

1. uma versão **desatualizada**, pedindo à réplica antes de x segundos;
2. uma versão **consistente**, pedindo à réplica depois de x segundos.

## Estrutura do projeto

```
.
├── Servidor_UDP.java           # Servidor + timer de replicação + console de alteração
├── Replica_UDP.java            # Réplica (só muda ao receber SYNC do servidor)
├── Cliente_UDP.java            # Cliente com menu: servidor, réplica ou comparação
├── temperaturas_servidor.txt   # Gerado pelo servidor na primeira execução
├── temperaturas_replica.txt    # Gerado pela réplica na primeira execução
├── download_servidor.txt       # Gerado pelo cliente ao baixar do servidor
└── download_replica.txt        # Gerado pelo cliente ao baixar da réplica
```

| Componente | Porta | Função |
|---|---|---|
| Servidor | 9870 | Atende `GET` dos clientes e envia `SYNC` à réplica a cada x segundos |
| Réplica | 9871 | Atende `GET` dos clientes e recebe `SYNC` do servidor |
| Cliente | aleatória | Envia `GET` ao servidor ou à réplica e salva o arquivo recebido |

## Formato do arquivo

Uma cidade por linha, no formato `Cidade;temperatura`:

```
Vitória da Conquista;21.5
Jequié;27.0
Guanambi;28.5
Brumado;26.0
Itapetinga;25.0
Barra da Estiva;22.0
Livramento de Nossa Senhora;24.5
```

## Protocolo (UDP, texto UTF-8)

| Mensagem | Origem → Destino | Resposta |
|---|---|---|
| `GET` | Cliente → Servidor ou Réplica | `VERSAO:n` + quebra de linha + conteúdo do arquivo |
| `SYNC:n` + quebra de linha + conteúdo | Servidor → Réplica | `ACK:n` |

O número `n` é a versão do arquivo. Ele sobe a cada alteração no servidor e permite comprovar, na demonstração, se o cliente recebeu uma versão atual ou antiga.

## Como funciona

- **Servidor:** guarda `versao` e `pendente`. Quando uma temperatura é alterada pelo console, a versão sobe e `pendente` vira `true`, mas nada é enviado à réplica. Um timer dispara a cada x segundos e, se houver alteração pendente, envia o arquivo à réplica e espera o `ACK` (timeout de 2s). Se falhar, tenta de novo no próximo ciclo.
- **Réplica:** só altera seu arquivo ao receber `SYNC`. Entre duas sincronizações, responde `GET` com a versão que tem, que pode estar desatualizada.
- **Cliente:** menu para escolher a origem do download; salva o resultado em `download_<origem>.txt`. A opção 3 baixa dos dois e compara as versões.
- **Concorrência:** o servidor tem três threads (atendimento, timer e console). Elas compartilham o arquivo e as variáveis de controle protegidos por `synchronized (LOCK)`.

## Requisitos

- JDK 8 ou superior
- Máquinas na mesma rede, com as portas UDP 9870 e 9871 liberadas no firewall

## Compilação

Na pasta do projeto, em cada máquina:

```
javac -encoding UTF-8 *.java
```

## Execução

Ordem: **Réplica → Servidor → Cliente**.

### 1) Réplica

```
java Replica_UDP [porta]
```

Exemplo: `java Replica_UDP` (usa a porta 9871).

### 2) Servidor

```
java Servidor_UDP [x_segundos] [ip_replica] [porta_replica]
```

Valores padrão: x = 15s, réplica em `localhost:9871`.

Exemplo com x = 10s e réplica em outra máquina:

```
java Servidor_UDP 10 <IP_DA_MAQUINA_DA_REPLICA> 9871
```

`<IP_DA_MAQUINA_DA_REPLICA>` deve ser substituído pelo **IP real da máquina onde a réplica está rodando** (veja a seção "Como descobrir o IP da máquina").

### 3) Cliente

```
java Cliente_UDP [ip_servidor] [ip_replica]
```

Valores padrão: `localhost` para ambos. Exemplo no laboratório:

```
java Cliente_UDP <IP_DA_MAQUINA_DO_SERVIDOR> <IP_DA_MAQUINA_DA_REPLICA>
```

Cada `<IP_...>` deve ser substituído pelo **IP real da máquina** onde o respectivo programa está rodando.

Menu do cliente:

| Opção | Ação |
|---|---|
| 1 | Baixar do Servidor Principal |
| 2 | Baixar da Réplica |
| 3 | Baixar dos dois e comparar as versões (`CONSISTENTE` ou `DESATUALIZADA`) |
| 0 | Sair |

### Como descobrir o IP da máquina

Em cada máquina que executar o servidor ou a réplica, descubra o IP na rede local:

- **Windows:** `ipconfig` e procure o campo **Endereço IPv4**
- **Linux/Mac:** `ip addr` (Linux) ou `ifconfig` (Mac)

Use esse IP nos comandos acima. As máquinas precisam estar na mesma rede, e `localhost` só funciona quando todos os programas estão na **mesma** máquina.

### Execução em uma única máquina

Abra três terminais na mesma pasta e rode, nesta ordem: `java Replica_UDP`, `java Servidor_UDP 10` e `java Cliente_UDP`.

## Console do servidor

Com o servidor rodando, digite no terminal dele:

| Comando | Efeito |
|---|---|
| `Jequié=35.0` | Altera a temperatura da cidade e sobe a versão |
| `listar` | Mostra o arquivo atual do servidor |
| `sync` | Força a replicação imediata, ignorando o timer |
| `sair` | Encerra o servidor |

O nome da cidade deve ser igual ao do arquivo, incluindo acentos.

## Roteiro de demonstração

1. Suba a réplica e o servidor (ex.: x = 10s) e abra o cliente.
2. No console do servidor, digite `Jequié=35.0`. O servidor passa para a versão 2.
3. No cliente, escolha **3**: aparece `DESATUALIZADA: servidor v2 x replica v1`, e a réplica ainda mostra Jequié com 27.0.
4. Aguarde o log `[TIMER] réplica atualizada para versão 2` no servidor.
5. No cliente, escolha **3** de novo: aparece `CONSISTENTE: ambos na versao 2`, e a réplica mostra Jequié com 35.0.

Observação: na primeira execução a réplica começa na versão 0 ("sem dados") e passa para a versão 1 na primeira sincronização, depois de x segundos.

## Parâmetros ajustáveis

| Onde | Constante | Significado |
|---|---|---|
| `Servidor_UDP.java` | `INTERVALO_X_SEGUNDOS` | Intervalo de replicação, ou seja, o desvio de idade máximo (padrão 15) |
| `Servidor_UDP.java` | `IP_REPLICA`, `PORTA_REPLICA` | Endereço da réplica |
| `Servidor_UDP.java` | `PORTA_SERVIDOR` | Porta do servidor (9870) |
| `Cliente_UDP.java` | `IP_SERVIDOR`, `IP_REPLICA` | Endereços usados caso não sejam passados por argumento |
| `Cliente_UDP.java` | `TIMEOUT_MS` | Tempo de espera por resposta (3000 ms) |

## Solução de problemas

- **`BindException: Address already in use`:** a porta está ocupada por uma execução anterior. No Windows, use `netstat -ano | findstr :9870` e `taskkill /PID <pid> /F`; ou use outra porta.
- **Cliente com "Sem resposta (timeout)":** confira os IPs, a ordem de inicialização e o firewall (UDP 9870 e 9871).
- **Acentos estranhos no terminal Windows:** execute `chcp 65001` antes de rodar.
- **"cidade não encontrada" no console:** o nome digitado precisa ser idêntico ao do arquivo.

## Limitações conhecidas

- UDP não garante entrega. O servidor usa `ACK` com timeout e reenvia no próximo ciclo se a réplica não confirmar.
- O arquivo precisa caber em um datagrama UDP (cerca de 65 KB).
- Leituras direto no servidor sempre veem o dado mais recente; apenas a réplica apresenta o desvio de idade.
