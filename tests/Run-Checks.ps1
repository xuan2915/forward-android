$ErrorActionPreference='Stop'
$cache="$env:USERPROFILE/.gradle/caches/modules-2/files-2.1"
$groups=@('org.jetbrains.kotlin/kotlin-compiler-embeddable','org.jetbrains.kotlin/kotlin-stdlib','org.jetbrains.kotlin/kotlin-reflect','org.jetbrains.kotlin/kotlin-script-runtime','org.jetbrains.intellij.deps/trove4j','org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm','org.jetbrains/annotations')
$jars=@(); foreach($g in $groups){$jars+=Get-ChildItem "$cache/$g" -Filter *.jar -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName}
$stdlib=Get-ChildItem "$cache/org.jetbrains.kotlin/kotlin-stdlib/2.0.21" -Filter *.jar -Recurse | Select-Object -First 1 -ExpandProperty FullName
$java=Join-Path $env:JAVA_HOME 'bin/java.exe'
& $java -cp ($jars -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath $stdlib -d build/rule-check app/src/main/java/com/focus/prototype/Rules.kt app/src/main/java/com/focus/prototype/PlanImport.kt tests/RulesCheck.kt tests/PlanImportCheck.kt
if($LASTEXITCODE -ne 0){throw 'Check compilation failed'}
foreach($entry in @('RulesCheckKt','PlanImportCheckKt')){& $java -cp "build/rule-check;$stdlib" "com.focus.prototype.$entry";if($LASTEXITCODE -ne 0){throw 'Check failed'}}
