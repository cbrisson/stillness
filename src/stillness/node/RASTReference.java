package stillness.node;

import org.apache.velocity.context.Context;
import org.apache.velocity.context.InternalContextAdapterImpl;
import org.apache.velocity.runtime.parser.node.ASTReference;
import org.apache.velocity.exception.MethodInvocationException;

import java.io.Reader;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;

import stillness.ScrapeContext;
import stillness.ScrapeException;
import stillness.StillnessConstants;
import stillness.Logger;

/**
 * @author Claude Brisson
 */
public class RASTReference extends RNode {

	public RASTReference() { }

    /**
     * We just want to synchronize the reference. The reference must already have a value
     */
    public boolean match(String source, Context context, ScrapeContext scrapeContext) throws ScrapeException {
        try {
            // call to value method handle the case in which the reference has childrens
            String value = (String) this.value(new InternalContextAdapterImpl(context));

            if (value == null || value.length() == 0) {
				if (scrapeContext.isDebugEnabled()) scrapeContext.getDebugOutput().logFailure(astNode.literal());
                return false;
			}

            if (scrapeContext.isSynchonized()) {
                if (source.startsWith(value, scrapeContext.getStart())) {
                    startIndex = scrapeContext.getStart();
                    // update the starting index
                    scrapeContext.incrStart(value.length());
					if (scrapeContext.isDebugEnabled())
						scrapeContext.getDebugOutput().logValue(astNode.literal(), value);
                    return true;
                }
            } else {
                startIndex = source.indexOf(value, scrapeContext.getStart());
                if (startIndex != -1) {
                    // update the starting index
                    scrapeContext.setStart(startIndex + value.length());
					if (scrapeContext.isDebugEnabled())
						scrapeContext.getDebugOutput().logValue(astNode.literal(), value);
                    return true;
                }
            }
        } catch (Exception e) {
			if (scrapeContext.isDebugEnabled()) scrapeContext.getDebugOutput().logFailure(astNode.literal());
            throw new ScrapeException("RASTReference match error : "+ e.getMessage() +" ("+ astNode.literal()+")");
        }

		if (scrapeContext.isDebugEnabled()) scrapeContext.getDebugOutput().logFailure(astNode.literal());
        return false;
    }

    /**
     * Set the reference in ScrapeContext indicating we are waiting for a setValue
     */
    public void scrape(String source, Context context, ScrapeContext scrapeContext) {
        // TODO: issue a warning if not synchronized
        // we want to get the value, nothing to do except set up the startIndex
        startIndex = scrapeContext.getStart();
        scrapeContext.setSynchronized(false);
        scrapeContext.setReference(this);
    }

    /**
     * Called by th RASTText following this reference in the tree.
     */
    public void setValue(String source, Context context, int end, ScrapeContext scrapeContext) throws ScrapeException {
        endIndex = end;
        String value = cleanValueBoundary(source.substring(startIndex, endIndex), (String)context.get("boundary"));
        String label = ((ASTReference)astNode).getRootString();

        // $junk case, ignore the data
        if (label.compareToIgnoreCase("junk") == 0) return;

        if (astNode.jjtGetNumChildren() == 0) {
            Object o = context.get(label);
            if (o == null) {
                context.put(label, value);
            	if (scrapeContext.isDebugEnabled()) {
                	scrapeContext.getDebugOutput().logValue(astNode.literal(),value);
        	    }
            } else {
                if (o instanceof List) {
                    ((List)o).add(value);
                } else {
                    ArrayList l = new ArrayList();
                    l.add(o);
                    l.add(value);
                    context.put(label, l);
                }
            }
            return;
        }

        // we have a $foo.bar in the template but $foo is null, we create a map
        if (context.get(((ASTReference)astNode).getRootString()) == null) {
            Properties p = new Properties();
            String key = astNode.jjtGetChild(0).literal(); // just one child for the moment
            p.put(key, value);
            context.put(label, p);
        } else { // we fall back on the velocity behaviour
            try {
                // handle all left cases
                ((ASTReference)astNode).setValue(new InternalContextAdapterImpl(context), source.substring(startIndex, endIndex));
            } catch (MethodInvocationException MIe) {
				if (scrapeContext.isDebugEnabled()) {
					scrapeContext.getDebugOutput().logFailure(astNode.literal());
				}
                throw new ScrapeException("RASTReference setValue error : "+ MIe.getMessage() +" ("+ astNode.literal()+")");
            }
        }

        if (scrapeContext.isDebugEnabled()) {
            scrapeContext.getDebugOutput().logValue(astNode.literal(),value);
        }
    }

    protected String cleanValueBoundary(String value, String boundary) {
        if (boundary == null || boundary.length() == 0) return value;

        value = value.trim();
        int start = 0;
        int end = value.length();
        for (int i=0; i<end; i++) {
            if (boundary.indexOf(value.charAt(i)) != -1) start = i+1;
            else break;
        }
        for (int i=end-1; i>start; i--) {
            if (boundary.indexOf(value.charAt(i)) != -1) end = i-1;
            else break;
        }

        return value.substring(start, end);
    }

}
