use strict;
use warnings;

my $name = "VS Code";
print "Hello from Perl in $name!\n";

sub factorial {
    my $n = shift;
    return 1 if $n <= 1;
    return $n * factorial($n - 1);
}

print "5! = ", factorial(5), "\n";