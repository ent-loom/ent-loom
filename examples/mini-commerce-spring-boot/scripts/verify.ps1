param(
    [string]$MavenRepository
)

$arguments = @()
if ($MavenRepository) {
    $arguments += "--maven-repository"
    $arguments += $MavenRepository
}
python scripts/verify.py @arguments
