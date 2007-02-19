/*
 * Copyright (C) 2005 Alfresco, Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.web.bean.spaces;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.transaction.UserTransaction;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.dictionary.TypeDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.web.app.AlfrescoNavigationHandler;
import org.alfresco.web.app.Application;
import org.alfresco.web.bean.content.DeleteContentDialog;
import org.alfresco.web.bean.dialog.BaseDialogBean;
import org.alfresco.web.bean.repository.MapNode;
import org.alfresco.web.bean.repository.Node;
import org.alfresco.web.bean.repository.Repository;
import org.alfresco.web.ui.common.Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Bean implementation for the "Delete Space" dialog
 * 
 * @author gavinc
 */
public class DeleteSpaceDialog extends BaseDialogBean
{
   private static final Log logger = LogFactory.getLog(DeleteContentDialog.class);
   
   private static final String DELETE_ALL = "all";
   private static final String DELETE_FILES = "files";
   private static final String DELETE_FOLDERS = "folders";
   private static final String DELETE_CONTENTS = "contents";
   
   private String deleteMode = DELETE_ALL; 
   

   // ------------------------------------------------------------------------------
   // Dialog implementation
   
   @Override
   protected String finishImpl(FacesContext context, String outcome)
         throws Exception
   {
      // get the space to delete
      Node node = this.browseBean.getActionSpace();
      if (node != null)
      {
         // force cache of name property so we can use it after the delete
         node.getName();
         
         if (logger.isDebugEnabled())
            logger.debug("Trying to delete space: " + node.getId() + " using delete mode: " + this.deleteMode);
         
         if (DELETE_ALL.equals(this.deleteMode))
         {
            this.nodeService.deleteNode(node.getNodeRef());
         }
         else
         {
            List<ChildAssociationRef> childRefs = this.nodeService.getChildAssocs(node.getNodeRef(), 
                  ContentModel.ASSOC_CONTAINS, RegexQNamePattern.MATCH_ALL);
            List<NodeRef> deleteRefs = new ArrayList<NodeRef>(childRefs.size());
            for (ChildAssociationRef ref : childRefs)
            {
               NodeRef nodeRef = ref.getChildRef();
               
               if (this.nodeService.exists(nodeRef))
               {
                  if (DELETE_CONTENTS.equals(this.deleteMode))
                  {
                     deleteRefs.add(nodeRef);
                  }
                  else
                  {
                     // find it's type so we can see if it's a node we are interested in
                     QName type = this.nodeService.getType(nodeRef);
                     
                     // make sure the type is defined in the data dictionary
                     TypeDefinition typeDef = this.dictionaryService.getType(type);
                     
                     if (typeDef != null)
                     {
                        if (DELETE_FOLDERS.equals(this.deleteMode))
                        {
                           // look for folder type
                           if (this.dictionaryService.isSubClass(type, ContentModel.TYPE_FOLDER) == true && 
                               this.dictionaryService.isSubClass(type, ContentModel.TYPE_SYSTEM_FOLDER) == false)
                           {
                              deleteRefs.add(nodeRef);
                           }
                        }
                        else if (DELETE_FILES.equals(this.deleteMode))
                        {
                           // look for content file type
                           if (this.dictionaryService.isSubClass(type, ContentModel.TYPE_CONTENT))
                           {
                              deleteRefs.add(nodeRef);
                           }
                        }
                     }
                  }
               }
            }
            
            // delete the list of refs
            TransactionService txService = Repository.getServiceRegistry(context).getTransactionService();
            for (NodeRef nodeRef : deleteRefs)
            {
               UserTransaction tx = null;
      
               try
               {
                  tx = txService.getNonPropagatingUserTransaction();
                  tx.begin();
                  
                  this.nodeService.deleteNode(nodeRef);
                  
                  tx.commit();
               }
               catch (Throwable err)
               {
                  try { if (tx != null) {tx.rollback();} } catch (Exception ex) {}
               }
            }
         }
      }
      else
      {
         logger.warn("WARNING: delete called without a current Space!");
      }
      
      return outcome;
   }
   
   @Override
   protected String doPostCommitProcessing(FacesContext context, String outcome)
   {
      Node node = this.browseBean.getActionSpace();
      
      if (node != null && this.nodeService.exists(node.getNodeRef()) == false)
      {
         // remove this node from the breadcrumb if required
         this.browseBean.removeSpaceFromBreadcrumb(node);
         
         // clear action context
         this.browseBean.setActionSpace(null);
         
         // setting the outcome will show the browse view again
         return AlfrescoNavigationHandler.CLOSE_DIALOG_OUTCOME + 
                AlfrescoNavigationHandler.OUTCOME_SEPARATOR + "browse";
      }
      else
      {
         return outcome;
      }
   }
   
   @Override
   protected String getErrorMessageId()
   {
      return "error_delete_space";
   }
   
   @Override
   public boolean getFinishButtonDisabled()
   {
      return false;
   }
   
   
   // ------------------------------------------------------------------------------
   // Bean Getters and Setters
   
   /**
    * Returns the confirmation to display to the user before deleting the content.
    * 
    * @return The formatted message to display
    */
   public String getConfirmMessage()
   {
      String fileConfirmMsg = Application.getMessage(FacesContext.getCurrentInstance(), 
               "delete_space_confirm");
      
      return MessageFormat.format(fileConfirmMsg, 
            new Object[] {this.browseBean.getActionSpace().getName()});
   }
   
   /**
    * @return Returns the delete operation mode.
    */
   public String getDeleteMode()
   {
      return this.deleteMode;
   }
   
   /**
    * @param deleteMode The delete operation mode to set.
    */
   public void setDeleteMode(String deleteMode)
   {
      this.deleteMode = deleteMode;
   }
}
