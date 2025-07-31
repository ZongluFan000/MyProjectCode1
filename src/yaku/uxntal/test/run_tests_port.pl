#!/usr/bin/env perl
use warnings;
use strict;
use v5.30;

use integer;

no warnings qw(experimental);
use feature qw(signatures);

use Data::Dumper;
use Carp;
use Getopt::Std;

my $yaku_port_command = $ENV{YAKU_PORT_CMD}; # 

my $yaku_command = 'perl -I../lib ../bin/yaku.pl';

my %opts = ();
getopts( 'vwVdahsq', \%opts );

if ($opts{'h'}) {
    die "-a tests assembler, default tests interpreter\n-q quits on first error\n-s <name> single .tal file\n";
}
# our $WW = $opts{'w'} ? 1 : 0;
# our $V = $opts{'v'} ? 1 : 0;
# our $VV = $opts{'V'} ? 1 : 0;
# our $DBG = $opts{'d'} ? 1 : 0;
our $testAssembler = $opts{'a'} ? 1 : 0; # default tests interpreter
our $quit = $opts{'q'} ? 1 : 0 ;
our $single = $opts{'s'} ? 1 : 0 ;
if ($single and not @ARGV) {
    die "Provide the .tal file to test on command line\n";
}
my @programFiles= $single ? ( $ARGV[0] ) : glob('*.tal');

for my $programFile (@programFiles) {
    my $romFile = $programFile;
    $romFile =~s/\.tal/.rom/;
    # next if $programFile=~/ex20_1_rel_abs_labels.tal/;
    next if $programFile=~/dbg/i;
        print $programFile, "\t";
    # open my $fh, '<', $programFile or die "Can't open file $!";
    my @ref = (); #`$yaku_command -W -r $programFile`;
    #`uxnasm $programFile tmp.rom && uxncli tmp.rom`;
    my $ref_str='';
    for my $refl (@ref) {
        chomp $refl;
        next if $refl=~/Unused/;
        next if $refl=~/macros/;
        $refl=~s/^\s+//msg;
        # say "<$refl>";
        $ref_str.=$refl;
    }
    my @res;
    if ($testAssembler) {
        @res = `$yaku_port_command -W -a $programFile && uxncli $romFile`
    } else {
        @res = `$yaku_port_command -W -r $programFile`;
    }
    my $res_str='';
    for my $resl (@res) {
        chomp $resl;
        $resl=~s/^\s+//msg;
        # say "<$resl>";
        $res_str.=$resl;
    }
    my $test_outcome = ($ref_str eq $res_str) ? 'PASS' : 'FAIL';
    say  "<$ref_str> <$res_str> :",$test_outcome;
    die if $quit and $test_outcome eq 'FAIL';

}

