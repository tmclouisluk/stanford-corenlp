
package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

import java.util.List;
import java.util.ArrayList;

/**
 * Implements the ACE <entity> construct
 */
public class AceEntity extends AceElement {
  private String mType;

  private String mSubtype;

  private String mClass;

  private List<AceEntityMention> mMentions;

  public AceEntity(String id,
		   String type,
		   String subtype,
		   String cls) {
    super(id);
    mType = type;
    mSubtype = subtype;
    mClass = cls;
    mMentions = new ArrayList<>();
  }
  
  public void addMention(AceEntityMention m) { 
    mMentions.add(m);
    m.setParent(this);
  }
  public List<AceEntityMention> getMentions() { return mMentions; }

  public String getType() { return mType; }
  public void setType(String s) { mType = s; }
  public String getSubtype() { return mSubtype; }
  public void setSubtype(String s) { mSubtype = s; }
  public void setClass(String s) { mClass = s; }
  public String getClasss() { return mClass; }

  public String toXml(int offset) {
    StringBuffer buffer = new StringBuffer();
    appendOffset(buffer, offset);
    buffer.append("<entity ID=\"").append(getId()).append("\" TYPE =\"").append(AceToken.OTHERS.get(mType)).append("\" SUBTYPE=\"").append(AceToken.OTHERS.get(mSubtype)).append("\" CLASS=\"").append(AceToken.OTHERS.get(mClass)).append("\">\n");

    for(AceEntityMention m: mMentions){
      buffer.append(m.toXml(offset + 2));
      buffer.append('\n');
    }

    appendOffset(buffer, offset);
    buffer.append("</entity>");
    return buffer.toString();
  }
}
