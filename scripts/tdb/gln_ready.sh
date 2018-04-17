#! /bin/bash
#
# Script that creates a list of auids that are ready to be pushed to the gln
#

tpath="/home/$LOGNAME/tmp"
#mkdir -p $tpath

plugin="lockss"
count=50 #output is up to twice this

# Make a list of AUids that are on ingest machine(s), and 'Yes' have substance, have crawled successfully.
   # Date of last successful crawl is unimportant because many good AUs have been frozen or finished.
   # Run this separately.
   #./scripts/tdb/ws_get_healthy.py machine login pw | sort > $tpath/gr_ingest_healthy.txt

# Make a list of AUids that are crawling in clockssingest, manifest in gln
   #set -x
   # Make a list of AUids from clockss
   #./scripts/tdb/tdbout -CZLI -a -Q "year ~ '^20' and plugin ~ '$plugin' and year !~ '2018'" tdb/clockssingest/*.tdb | grep -v TaylorAndFrancisPlugin | sort > $tpath/gr_clockss_c.txt
   ./scripts/tdb/tdbout -CZLIF -a -Q "year ~ '^20' and plugin ~ '$plugin' and year !~ '2018'" tdb/clockssingest/*.tdb | sort > $tpath/gr_clockss_c.txt
   # Make a list of AUids from gln
   #./scripts/tdb/tdbout -M -a -Q "year ~ '^20' and plugin ~ '$plugin' and year !~ '2018'" tdb/prod/*.tdb | grep -v TaylorAndFrancisPlugin | sort > $tpath/gr_gln_m.txt
   ./scripts/tdb/tdbout -M -a -Q "year ~ '^20' and plugin ~ '$plugin' and year !~ '2018'" tdb/prod/*.tdb | sort > $tpath/gr_gln_m.txt

   # Convert the gln list to clockss format, and start a list
   cat $tpath/gr_gln_m.txt | sed -e 's/\(\|[^\|]*\)Plugin/Clockss\1Plugin/' | sort > $tpath/gr_gln_mc.txt
   # Find common items on the clockss list and the clockss-formatted gln list
   comm -12 $tpath/gr_clockss_c.txt $tpath/gr_gln_mc.txt > $tpath/gr_common.txt

   # Also convert the https items to http items, convert to clockss format, and start a list
   cat $tpath/gr_gln_m.txt | grep https%3A | grep -v ProjectMuse2017Plugin | sed -e 's/https/http/' | sed -e 's/\(\|[^\|]*\)Plugin/Clockss\1Plugin/' | sort > $tpath/gr_gln_mcs.txt
   # Find common items on the clockss list and the clockss-formatted gln list
   comm -12 $tpath/gr_clockss_c.txt $tpath/gr_gln_mcs.txt > $tpath/gr_common_s.txt
   # Convert back to https and merge in with the rest of the AUs.
   #cat $tpath/gr_common_s.txt | sed 's/http/https/' >> $tpath/gr_common.txt

   #set +x

# Document Errors. AUs that are in the GLN but not in clockss
   echo "********ERRORS********" > $tpath/gr_errors.txt #create error file 
   #echo "***Manifest in GLN, but not Crawling in Clockss***" >> $tpath/gr_errors.txt
   #comm -13 $tpath/gr_clockss_c.txt $tpath/gr_gln_mc.txt >> $tpath/gr_errors.txt
   #echo "***Not Manifest in GLN, but Crawling in Clockss***" >> $tpath/gr_errors.txt
   #comm -23 $tpath/gr_clockss_c.txt $tpath/gr_gln_mc.txt >> $tpath/gr_errors.txt

# Find items not healthy on the ingest machines.
   #echo "***M on gln. C on clockss. Not healthy on ingest machines.***" >> $tpath/gr_errors.txt
   #comm -13 $tpath/gr_ingest_healthy.txt $tpath/gr_common.txt >> $tpath/gr_errors.txt
   # Find common items on the list of AUs with manifest pages, and the list of healthy AUs on the ingest machines.
   
# Find items healthy on the ingest machines.
   comm -12 $tpath/gr_ingest_healthy.txt $tpath/gr_common.txt > $tpath/gr_common_healthy.txt
   comm -12 $tpath/gr_ingest_healthy.txt $tpath/gr_common_s.txt > $tpath/gr_common_healthy_s.txt

# Select a random collection of clockss AUids
   shuf $tpath/gr_common_healthy.txt | head -"$count" > $tpath/gr_common_shuf.txt
   #After health check, convert back to https and merge lists together
   shuf $tpath/gr_common_healthy_s.txt | sed 's/http/https/' | head -"$count" >> $tpath/gr_common_shuf.txt
   #cat $tpath/gr_common_shuf_s1.txt | sed -e 's/http/https/' > $tpath/gr_common_shuf_s2.txt #this one is https. For manifest page finding.

# FOR All AUs
# Does AU have a clockss and gln manifest page?
   # Look for clockss manifest pages for the previously selected set.
   ./scripts/tdb/read_auid_new.pl $tpath/gr_common_shuf.txt > $tpath/gr_man_clks.txt
   cat $tpath/gr_man_clks.txt | grep "*N" >> $tpath/gr_errors.txt
   cat $tpath/gr_man_clks.txt | grep "*M" | sed -e 's/.*, \(org|lockss|plugin|[^,]*\), .*/\1/' > $tpath/gr_found_cl.txt
   # Convert the list from clockss to gln
   cat $tpath/gr_found_cl.txt | sed -e 's/Clockss\([^\|]*\)Plugin/\1Plugin/' > $tpath/gr_found_cl_g.txt
   # Look for lockss manifest pages for AUids that have clockss manifest pages.
   ./scripts/tdb/read_auid_new.pl $tpath/gr_found_cl_g.txt > $tpath/gr_man_gln.txt
   cat $tpath/gr_man_gln.txt | grep "*N" >> $tpath/gr_errors.txt
   cat $tpath/gr_man_gln.txt | grep "*M" | sed -e 's/.*, \(org|lockss|plugin|[^,]*\), .*/\1/' > $tpath/gr_found_gln.txt

# FOR AUs THAT ARE HTTPS FOR GLN AND HTTP FOR CLOCKSS
# For clockss, manifest pages need to be looked for under https, because they will show as redirected otherwise.
# Does AU have a clockss and gln manifest page?
   # Look for clockss manifest pages for the previously selected set.
   #./scripts/tdb/read_auid_new.pl $tpath/gr_common_shuf_s2.txt > $tpath/gr_man_clks.txt
   #cat $tpath/gr_man_clks.txt | grep "*N" >> $tpath/gr_errors.txt
   #cat $tpath/gr_man_clks.txt | grep "*M" | sed -e 's/.*, \(org|lockss|plugin|[^,]*\), .*/\1/' > $tpath/gr_found_cl.txt
   # Convert the list from clockss to gln
   #cat $tpath/gr_found_cl.txt | sed -e 's/https/http/' | sed -e 's/Clockss\([^\|]*\)Plugin/\1Plugin/' > $tpath/gr_found_cl_g.txt
   # Look for lockss manifest pages for AUids that have clockss manifest pages.
   #./scripts/tdb/read_auid_new.pl $tpath/gr_found_cl_g.txt > $tpath/gr_man_gln.txt
   #cat $tpath/gr_man_gln.txt | grep "*N" >> $tpath/gr_errors.txt
   #cat $tpath/gr_man_gln.txt | grep "*M" | sed -e 's/.*, \(org|lockss|plugin|[^,]*\), .*/\1/' > $tpath/gr_found_gln_s.txt

# Output
   cat $tpath/gr_found_gln.txt
   #cat $tpath/gr_found_gln_s.txt
   cat $tpath/gr_errors.txt

exit 0

# Example successful AUid
# org|lockss|plugin|highwire|ClockssHighWirePressH20Plugin&base_url~http%3A%2F%2Fhpq%2Esagepub%2Ecom%2F&volume_name~20
# org|lockss|plugin|highwire|HighWirePressH20Plugin&base_url~http%3A%2F%2Fhpq%2Esagepub%2Ecom%2F&volume_name~20
