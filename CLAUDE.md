# SkibCommon

Plugin Paper único (`skibidilandia.SkibCommon`) que agrupa vários subsistemas do servidor
Skibidilândia. Cada subsistema fica em seu próprio pacote sob `src/main/java/skibidilandia/`
(ex.: `minemagic/`, e os demais módulos de ferramentas/itens) e é ligado no `onEnable` do
`SkibCommon` através de uma classe `*Plugin` que registra itens, listeners e comandos.

- **API alvo:** Paper `26.1.2` (ver `pom.xml` e `api-version` no `plugin.yml`).
- **Build:** Maven. O artefato sai como `target/SkibCommon-1.0-SNAPSHOT.jar`.

## Deploy obrigatório após mexer em QUALQUER plugin

O servidor carrega `../skibidilandia/plugins/SkibCommon.jar`, **não** o `target/`. Compilar
sozinho não tem efeito no jogo — é por isso que mudanças "somem". Sempre que alterar código
de um plugin (novo item, listener, comando, etc.), por padrão rode o build e copie o jar:

```bash
cd skib_common
mvn -o package -DskipTests
cp target/SkibCommon-1.0-SNAPSHOT.jar ../skibidilandia/plugins/SkibCommon.jar
```

(`-o` = offline; as dependências do Paper já estão no cache local. Em PowerShell, troque
`cp` por `Copy-Item target\SkibCommon-1.0-SNAPSHOT.jar ..\skibidilandia\plugins\SkibCommon.jar`.)

Para que as mudanças entrem em vigor o servidor precisa recarregar o plugin (restart do
servidor ou reload). Confirme que o jar foi de fato atualizado conferindo o timestamp de
`../skibidilandia/plugins/SkibCommon.jar`.

## Padrão dos itens mágicos (`minemagic/`)

Itens são identificados por uma chave no PDC (sobrevive a soltar/pegar/encantar), nunca pelo
nome/lore. Para adicionar um item novo, siga o que já existe (ex.: o Arco Élfico):

1. `MineMagicItems.java` — adicione `Material`, um `NamespacedKey` (inicializado em `init`),
   um `createX()` (display name, lore, flags, grava a chave no PDC) e um `isX(ItemStack)`.
2. `XListeners.java` — uma classe `Listener` com o comportamento; cheque `MineMagicItems.isX(...)`
   antes de agir. Exponha `shutdown()` (mesmo que vazio) por simetria.
3. `MineMagicPlugin.java` — campo do listener, instanciação + `registerEvents` em `register()`,
   chamada de `shutdown()`, um `case` em `build()` e a entrada nas listas de usage/tab-complete.
4. `plugin.yml` — atualize a `usage`/`description` do comando `minemagic`.

Entrega in-game: `/minemagic dar <item> [jogador]` (alias `/mm`).
