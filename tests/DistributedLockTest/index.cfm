<cfscript>
    cacheName=url.cache?:"loc";
    setting showdebugoutput=false requesttimeout=100;
    start=getTickCount();
    try {   
     
        echo("before;");
        if(isNull(server[name])) {
            lock name=name {
                if(isNull(server[name])) server[name]={counter:0,counters:[],threads:[]};
            }
        }
        

        //lock name=name {
        DistributedLock name=name cache=cacheName amount=url.amount?:1 expires=10 timeout=url.timeout?:10 {
            lock name=name {
                arrayAppend(server[name].counters,++server[name].counter);
                arrayAppend(server[name].threads,getPageContext().getThread().getName());
            }
            echo("inside;");
            sleep(url.time?:100);

            lock name=name {
                arrayAppend(server[name].counters,--server[name].counter);
                arrayAppend(server[name].threads,getPageContext().getThread().getName());
            }
            if(url.throw?:false) throw "throw inside the lock";
            if(url.abort?:false) abort;
        }
        echo("after;");
        echo(getTickCount()-start);
    }
    catch(e) {
        systemOutput(e,1,1)
        echo("exception;");
        echo(getTickCount()-start);
    }
</cfscript>