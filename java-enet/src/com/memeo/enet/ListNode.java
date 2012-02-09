package com.memeo.enet;

/**
 * Base class for linked-list-node objects.
 * @author csm
 *
 * @param <T> The type subclassing or using this node.
 */
class ListNode<T extends ListNode<T>>
{
    ListNode<T> previous = null;
    ListNode<T> next = null;
    
    /**
     * Removes this node from the list.
     */
    void remove()
    {
        if (this.previous != null)
            this.previous.next = this.next;
        if (this.next != null)
            this.next.previous = this.previous;
    }
    
    /**
     * Appends a node to the end of this list.
     * @param node The node to append.
     */
    void append(ListNode<T> node)
    {
        tail().insert(node);
    }
    
    /**
     * Inserts a node after this node.
     * 
     * @param node
     */
    void insert(ListNode<T> node)
    {
        node.previous = this;
        node.next = this.next;
        if (node.next != null)
            node.next.previous = node;
        this.next = node;
    }
    
    /**
     * Returns the head of this list.
     * @return
     */
    ListNode<T> head()
    {
        ListNode<T> current = this;
        while (current.previous != null)
            current = current.previous;
        return current;
    }
    
    /**
     * Returns the tail of this list.
     * @return
     */
    ListNode<T> tail()
    {
        ListNode<T> current = this;
        while (current.next != null)
            current = current.next;
        return current;
    }
    
    /**
     * Clears this list.
     */
    void clear()
    {
        head().next = null;
    }
    
    /**
     * Return the size of this list.
     * @return
     */
    int size()
    {
        int ret = 0;
        ListNode<T> current = head();
        while (current.next != null)
        {
            ret++;
            current = current.next;
        }
        return ret;
    }
}
