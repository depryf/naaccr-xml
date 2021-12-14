%MACRO writeNaaccrXml(libpath, targetfile, naaccrversion="", recordtype="", dataset=alldata, items="", dictfile="", dictUri="", writenum="no", cleanupcsv="yes", grouptumors="yes");

/************************************************************************************************************;
    This macro writes a given data fileset into a NAACCR XML data file.

    Paramaters:
	- libpath needs to point to the Java SAS library (path can be relative or absolute)
	- targetfile needs to point to the XML to export  (path can be relative or absolute);
	    -- if the path ends with ".gz" it will be created as a GZIP compressed file
	    -- otherwise it will be created as an uncompressed file
	- naaccrversion should be one of the supported NAACCR versions provided as three digits:
	    "140", "150", "160", etc... (this parameter is required, no default);
	    make sure to provide the proper version or some items might be dropped during the writing process
	- recordtype should be "A", "M", "C" or "I" (required, no default);
	     make sure to provide the proper type or some items might be dropped during the writing process
    - dataset should be the name of the dataset from which the data should be taken (defaults to alldata)
    - items is an optional list of items to write (any items not in the list will be ignored);
        if not provided, the all items in the data set will be written.
        There are two ways to provide the list:
            1. Hard code the XML IDs in the SAS code, separate them with a comma:
                   items="patientIdNumber,tumorRecordNumber,primarySite"
            2. Provide the path (relative or absolute) to a CSV file:
                   items="included-items.csv"
               The first line of the file must be headers; the XML IDs to include are expected to be found
               in the first column (the file can contain other columns); a simple file would look like this:
                   NAACCR_XML_ID
                   patientIdNumber
                   tumorRecordNumber
                   primarySite
       The NAACCR XML IDs for the standard items can be found on the NAACCR website.
    - dictfile is the path to an optional user-defined dictionary in CSV format (the NAACCR XML Tool that
        is distributed with the macros has an option to load an XML dictionary and save it as CSV);
        File*Pro can also generate those files); use spaces to separate multiple paths if you need to
        provide more than one dictionary
    - dictUri is an optional user-defined dictionary URI to reference in the created XML file (if a CSV dictionary
        is provided, then this one should be provided as well); the URI can be found as a root attribute of the
        XML dictionary (it usually looks like an internet address, but it's rarely a legit address; and the macros
        do not try to connect to that address in any way). Use spaces to separate multiple URIs.
    - writenum should be "yes" or "no" (defaults to "no"); if "yes" then the NAACCR numbers will be written.
    - cleanupcsv should be "yes" or "no" (defaults to "yes"); if "no" then the tmp CSV file won't be
        automatically deleted; use this parameter to QC the CSV file or use it to investigate problems.
    - grouptumors should be "yes" or "no" (defaults to "yes"); if "yes" then the tumors that have the same
        patient ID number (and appearing together in the observations) will be grouped under one Patient tag;
        if "no", each tumor will appear under its own Patient tag (and every Patient will contain exactly one Tumor).


    Note that the macro creates a tmp CSV file in the same folder as the target file; that file will be 
    automatically deleted by the macro when it's done executing (unless the 'cleanupcsv' parameter is set to 'no').

    A typical use-case for the write macro is to read an XML file (using the read macro), do something to the data
    and write it back. But an another common use-case is to start from an existing data set. In that case, there are
    a few caveats to keep in mind:
     - Variable names must be the NAACCR XML IDs (any other variable will be ignored).
     - Every observation (which represent Tumors) that belong to the same Patient need to have the same value for
       the "patientIdNumber" variable (otherwise every Tumor will end up in its own Patient and there won’t be
       any Tumor grouping done).
     - The Patient values are taken from the first observation (the first Tumor) of that Patient.
     - The NaaccrData values (the items appearing only once per file) are taken from the first observation.

    Changelog
    *********
    06/10/2018 - Fabian Depry - Initial version.
    07/31/2018 - Fabian Depry - Added new optional parameter for user-defined dictionary.
    12/08/2019 - Fabian Depry - Fixed a mistake in this comment, no change to the behavior of the macro.
    04/22/2020 - Fabian Depry - Added new dictUri parameter needed to properly re-create XML data files.
    09/29/2020 - Fabian Depry - Renamed dictUri to dicturi; fixed the macro crashing when the param is not provided.
    02/16/2021 - Fabian Depry - Fixed documentation missing version 210, no change to the actual code.
    03/12/2021 - Fabian Depry - Removed default value for version which was incorrectly set to 180.
    03/12/2021 - Fabian Depry - Removed default value for record type instead of assuming "I" for incidence.
    03/12/2021 - Fabian Depry - Added new writenum parameter to allow NAACCR numbers to be written.
    04/13/2021 - Fabian Depry - Added documentation for providing included items as a CSV file.
    10/08/2021 - Fabian Depry - Added new optional cleanupcsv parameter to allow better QC and problem investigation.
    12/14/2021 - Fabian Depry - Added new optional grouptumors parameter to allow not grouping the tumors.
 ************************************************************************************************************/;

/*
   Tell SAS where to find the Java library file.
*/
options set=CLASSPATH &libpath;

/*
   Call the Java library to known the name and location of the CSV file it will expect.
*/
data _null_;
    attrib csvpath length = $200;
    declare JavaObj j1 ('com/imsweb/naaccrxml/sas/SasCsvToXml', &targetfile, &naaccrversion, &recordtype);
    j1.callStringMethod('getCsvPath', csvpath);
    call symput('csvfile', csvpath);
    j1.delete();
run;

/*
   Export the dataset into a CSV file.
*/
proc export data=&dataset
   outfile="&csvfile"
   dbms=csv
   replace;
run;

/*
   Call the Java library to convert the CSV file into an XML file; delete the CSV file once we are done.
*/
data _null_;
    declare JavaObj j1 ('com/imsweb/naaccrxml/sas/SasCsvToXml', &targetfile, &naaccrversion, &recordtype);
    j1.callVoidMethod('setDictionary', &dictfile, &dicturi);
    j1.callVoidMethod('setWriteNumbers', &writenum);
    j1.callVoidMethod('setGroupTumors', &grouptumors);
    j1.callVoidMethod('convert', &items);
    j1.callVoidMethod('cleanup', &cleanupcsv);
    j1.delete();
run;


%MEND writeNaaccrXml;