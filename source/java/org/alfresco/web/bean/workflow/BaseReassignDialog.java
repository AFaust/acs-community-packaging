/*
 * Copyright (C) 2005-2007 Alfresco Software Limited.
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
 * http://www.alfresco.com/legal/licensing
 */
package org.alfresco.web.bean.workflow;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.transaction.UserTransaction;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.search.impl.lucene.QueryParser;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.web.app.Application;
import org.alfresco.web.bean.dialog.BaseDialogBean;
import org.alfresco.web.bean.repository.Repository;
import org.alfresco.web.ui.common.SortableSelectItem;
import org.alfresco.web.ui.common.Utils;

/**
 * Base dialog bean for workflow user reassignment.
 * 
 * @author gavinc
 * @author Kevin Roast
 */
public abstract class BaseReassignDialog extends BaseDialogBean
{
   transient private WorkflowService workflowService;
   transient private PersonService personService;
   
   
   // ------------------------------------------------------------------------------
   // Bean Getters and Setters

   /**
    * Sets the workflow service to use
    * 
    * @param workflowService The WorkflowService instance
    */
   public void setWorkflowService(WorkflowService workflowService)
   {
      this.workflowService = workflowService;
   }
   
   protected WorkflowService getWorkflowService()
   {
      if (workflowService == null)
      {
         workflowService = Repository.getServiceRegistry(FacesContext.getCurrentInstance()).getWorkflowService();
      }
      return workflowService;
   }
   
   /**
    * @param personService   The PersonService to set.
    */
   public void setPersonService(PersonService personService)
   {
      this.personService = personService;
   }
   
   protected PersonService getPersonService()
   {
      if (personService == null)
      {
         personService = Repository.getServiceRegistry(FacesContext.getCurrentInstance()).getPersonService();
      }
      return personService;
   }
   
   /**
    * Property accessed by the Generic Picker component.
    * 
    * @return the array of filter options to show in the users/groups picker
    */
   public SelectItem[] getFilters()
   {
      ResourceBundle bundle = Application.getBundle(FacesContext.getCurrentInstance());
      return new SelectItem[] {new SelectItem("0", bundle.getString("users"))};
   }
   
   /**
    * Query callback method executed by the Generic Picker component.
    * This method is part of the contract to the Generic Picker, it is up to the backing bean
    * to execute whatever query is appropriate and return the results.
    * 
    * @param filterIndex        Index of the filter drop-down selection
    * @param contains           Text from the contains textbox
    * 
    * @return An array of SelectItem objects containing the results to display in the picker.
    */
   public SelectItem[] pickerCallback(int filterIndex, String contains)
   {
      FacesContext context = FacesContext.getCurrentInstance();
      
      SelectItem[] items;
      
      UserTransaction tx = null;
      try
      {
         tx = Repository.getUserTransaction(context, true);
         tx.begin();

         // Use lucene search to retrieve user details
         String term = QueryParser.escape(contains.trim());
         StringBuilder query = new StringBuilder(128);
         query.append("@").append(NamespaceService.CONTENT_MODEL_PREFIX).append("\\:firstName:\"*");
         query.append(term);
         query.append("*\" @").append(NamespaceService.CONTENT_MODEL_PREFIX).append("\\:lastName:\"*");
         query.append(term);
         query.append("*\" @").append(NamespaceService.CONTENT_MODEL_PREFIX).append("\\:userName:");
         query.append(term);
         query.append("*");
         ResultSet resultSet = Repository.getServiceRegistry(context).getSearchService().query(
                 Repository.getStoreRef(), SearchService.LANGUAGE_LUCENE, query.toString());
         List<NodeRef> nodes = resultSet.getNodeRefs();
         
         ArrayList<SelectItem> itemList = new ArrayList<SelectItem>(nodes.size());
         for (NodeRef personRef : nodes)
         {
            String username = (String)getNodeService().getProperty(personRef, ContentModel.PROP_USERNAME);
            if (PermissionService.GUEST_AUTHORITY.equals(username) == false)
            {
               String firstName = (String)getNodeService().getProperty(personRef, ContentModel.PROP_FIRSTNAME);
               String lastName = (String)getNodeService().getProperty(personRef, ContentModel.PROP_LASTNAME);
               SelectItem item = new SortableSelectItem(username, firstName + " " + lastName + " [" + username + "]", lastName);
               itemList.add(item);
            }
         }
         items = new SelectItem[itemList.size()];
         itemList.toArray(items);
         
         Arrays.sort(items);
         
         // commit the transaction
         tx.commit();
      }
      catch (Throwable err)
      {
         Utils.addErrorMessage(MessageFormat.format(Application.getMessage(
               FacesContext.getCurrentInstance(), Repository.ERROR_GENERIC), err.getMessage()), err);
         try { if (tx != null) {tx.rollback();} } catch (Exception tex) {}
         
         items = new SelectItem[0];
      }
      
      return items;
   }
}