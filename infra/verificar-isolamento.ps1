# Confere o ADR-0003: o usuário de cada serviço só acessa o próprio schema MySQL.
# Uso (com a infra no ar, dentro de infra/):  .\verificar-isolamento.ps1
$servicos = "catalogo", "carrinho", "pedido", "pagamento", "estoque"
$falhas = 0

function Invoke-MySql($usuario, $sql) {
    # A senha de desenvolvimento de cada usuário é o próprio nome (ver mysql/init.sql).
    $saida = docker compose exec -T mysql mysql "-u$usuario" "-p$usuario" -N -e $sql 2>&1 |
        Where-Object { $_ -notmatch "Using a password on the command line" }
    return @{ Ok = ($LASTEXITCODE -eq 0); Saida = ($saida -join " ") }
}

foreach ($servico in $servicos) {
    $proprio = Invoke-MySql $servico "CREATE TABLE IF NOT EXISTS $servico.verificacao (id INT); DROP TABLE $servico.verificacao;"
    if ($proprio.Ok) {
        Write-Host "ok    $servico cria e apaga tabela no schema $servico" -ForegroundColor Green
    } else {
        Write-Host "FALHA $servico não consegue usar o próprio schema: $($proprio.Saida)" -ForegroundColor Red
        $falhas++
    }

    foreach ($outro in $servicos | Where-Object { $_ -ne $servico }) {
        $alheio = Invoke-MySql $servico "SHOW TABLES FROM $outro;"
        if ($alheio.Ok) {
            Write-Host "FALHA $servico consegue ler o schema $outro" -ForegroundColor Red
            $falhas++
        }
    }
    if ($falhas -eq 0) { Write-Host "ok    $servico não acessa nenhum outro schema" -ForegroundColor Green }
}

if ($falhas -gt 0) { Write-Host "`n$falhas falha(s) de isolamento" -ForegroundColor Red; exit 1 }
Write-Host "`nIsolamento entre schemas confirmado." -ForegroundColor Green
exit 0
