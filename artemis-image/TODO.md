TODO:
 - replace @classpath_file with cp from libs/*.jar directory - more extensible for folks that want extra jars/plugins in derived images
   - or figure out a mount point for extra jars
 - currently running as root!
   - maybe we need a base image with an 'app' or 'java' user preconfigured with rw permissions on /app
   - or do that in a launch.sh
     - this is a known and reasonable limitation of jib https://github.com/GoogleContainerTools/jib/issues/1029